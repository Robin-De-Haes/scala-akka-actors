package services

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout}
import domain.ProductContainers.{Purchase, StockHouseShipment}
import domain.{Client, Product}
import services.helpers.PrimePriorityQueue
import services.messages.orderprocessing.{NearestStockHouses, OrderDelayed, OrderShipped, PurchaseConfirmed}
import services.messages.stockhouse._

import scala.concurrent.duration.DurationInt

// Object with necessary information to easily create ProcessingServices
// It knows the StockHouseServices it can contact
object ProcessingService {
  def props(stockHouseServices: List[ActorRef], numberOfStockHouses : Int = 5): Props =
    Props(new ProcessingService(stockHouseServices, numberOfStockHouses))
  def name(name: String = "ProcessingService"): String = name
}

// ProcessingService handles all communication necessary for processing a Purchase.
// It communicates with ClientServices, knows all available StockHouseServices and uses ephemeral children internally to
// aggregate responses from these StockHouseServices.
class ProcessingService(stockHouseServiceRefs: List[ActorRef], numberOfStockHousesToCheck : Int = 5)
  extends Actor with ActorLogging {
  // We use a custom priority queue of (Client, Purchase, ActorRef) tuples with hasPrime as the priority.
  // We give true a higher priority than false, which causes the Purchases of Prime subscribers to be prioritized.
  // We process the purchases (of which each can contain multiple products) sequentially to avoid problems with
  // multiple purchases requesting the same products from the same StockHouses.
  var purchaseQueue : PrimePriorityQueue = new PrimePriorityQueue()

  // Option Field to keep track of the address for which there currently is a Purchase being processed,
  // None means there currently is nothing being processed (so the Processing Service is not busy).
  var processing : Option[(Client, Purchase, ActorRef)] = None

  /**
   * Helper method to start processing the specified Purchase for the given Client.
   * The behavior of the Actor is not changed so we can still receive new PurchaseConfirmed messages,
   * while the current Purchase is being processed.
   *
   * @param client Client to process the Purchase for
   * @param purchase Purchase to process
   * @param replyTo ActorRef where a message should be sent to regarding notable events when processing the Purchase
   */
  def startProcessingPurchase(client: Client, purchase : Purchase, replyTo: ActorRef) : Unit = {
    // we are now processing a Purchase
    processing = Some((client, purchase, replyTo))

    // create an aggregator to find the nearest StockHouses (which is the first part of processing)
    findNearestStockHouses(client, purchase)
  }

  /**
   * Helper method to finish processing the Purchase.
   * It will cause the next Purchase to start processing if there is one,
   * otherwise it will clear the processing field to indicate the ProcessingService is no longer busy.
   */
  def finishProcessingPurchase(): Unit = {
    if (purchaseQueue.isEmpty) {
      processing = None
    }
    else {
      processNextPurchase()
    }
  }

  /**
   * Helper method to get the next Purchase to process from the queue and start processing it.
   * This method does not check before dequeueing if there actually is a Purchase to process available.
   */
  def processNextPurchase() : Unit = {
    // get the next Purchase to process
    val (client, purchase, clientService) = purchaseQueue.dequeue()
    // process it
    startProcessingPurchase(client, purchase, clientService)
  }

  /**
   * Helper method that causes an error to be logged in case a message needs to be send during/after processing
   * a Purchase but a valid replyTo address is missing.
   * This event should not occur, but is added as a safety and debugging measure.
   */
  def sendProcessingReply(forClient: Client, forPurchaseID : String, message : Any) : Unit ={
    processing.map {
      case (client, purchase, replyTo) =>
        if (forClient == client && forPurchaseID == purchase.id) {
          replyTo ! message
          replyTo
        }
    }.getOrElse
    {
      log.error(s"Processing client or purchase is invalid, while trying to send the message $message")
    }
  }

  /**
   * Helper function to create an ephemeral child for aggregating the calculated distance responses
   * from the StockHouseServices.
   *
   * @param client Client to which the distance has to be calculated
   * @param purchase Purchase that has to be fulfilled by the StockHouses
   */
  def findNearestStockHouses(client: Client, purchase: Purchase) : Unit = {
    if (stockHouseServiceRefs.size > numberOfStockHousesToCheck) {
      // create ephemeral aggregator to handle the responses
      val aggregator = context.actorOf(Props(new NearestNeighborsAggregator(client, numberOfStockHousesToCheck, purchase, self)))
      // request the distance to the client from all StockHouseServices (and let them respond to the aggregator)
      stockHouseServiceRefs.foreach(_ ! ManagerQuery(DistanceTo(client), purchase.id, aggregator))
    }
    else {
      // if we have less StockHouseServices than requested neighbors, just return all StockHouseServices
      self ! NearestStockHouses(client, purchase, stockHouseServiceRefs)
    }
  }

  override def receive: Receive = {
    case PurchaseConfirmed(client: Client, purchase: Purchase) => {
      if (purchaseQueue.isEmpty && processing.isEmpty){
        // process the received Purchase (since there are no other Purchases to process right now)
        startProcessingPurchase(client, purchase, sender)
      } else {
        // enqueue the received Purchase to process it later based on its priority (since the ProcessingService is busy)
        purchaseQueue.enqueue((client, purchase, sender))
      }
    }
    case NearestStockHouses(client: Client, purchase : Purchase, nearestStockHouses : List[ActorRef]) => {
      // if something went wrong and no StockHouses that are near were found, we immediately delay the order
      if (nearestStockHouses.isEmpty) {
        sendProcessingReply(client, purchase.id, OrderDelayed(client, purchase))
        finishProcessingPurchase()
      }

      // the nearest stock houses have been identified, so now create another ephemeral child
      // for aggregating the responses for filling the order
      val aggregator = context.actorOf(Props(new FillOrderAggregator(client, purchase, self)))
      nearestStockHouses.foreach(_ ! ManagerCommand(FillOrder(purchase), purchase.id, aggregator))
    }
    case OrderShipped(client, purchase, shippedGoods) => {
      // inform the Client the Order has been shipped and start processing the next Purchase
      sendProcessingReply(client, purchase.id, OrderShipped(client, purchase, shippedGoods))
      finishProcessingPurchase()
    }
    case OrderDelayed(client : Client, purchase: Purchase) => {
      // inform the client the Order is delayed and start processing the next Purchase
      sendProcessingReply(client, purchase.id, OrderDelayed(client, purchase))
      finishProcessingPurchase()
    }
  }
}

// Ephemeral child used for aggregating the results of distance calculations in order to return the
// StockHouseService addresses of the nearest StockHouses
class NearestNeighborsAggregator(client: Client, numberOfNeighbors : Int, purchase: Purchase, replyTo: ActorRef)
  extends Actor {

  // we will wait for the specified time on responses from the StockHouseServices before terminating
  context.setReceiveTimeout(3.seconds)

  def receive: Receive = receiveDistances(Map.empty)

  def receiveDistances(stockHouseServiceRefs: Map[ActorRef, Double]): Receive = {
    case ManagerResult(_, DistanceToResult(_, distance)) =>
      // keep track of the StockHouseService and its respective StockHouse's distance to the Client
      context.become(receiveDistances(stockHouseServiceRefs.updated(sender, distance)))
    case ReceiveTimeout =>
      // take the specified number of StockHouses from the values (sorted on distance)
      val nearestStockHouseServiceRefs =
        stockHouseServiceRefs.toSeq.sortBy(_._2).take(numberOfNeighbors).map(_._1).toList

      // send back the service addresses for the nearest StockHouses that were found and stop yourself
      // (since the aggregation is finished)
      replyTo ! NearestStockHouses(client, purchase, nearestStockHouseServiceRefs)
      context.stop(self)
  }
}

// Ephemeral child used for aggregating the results of stock reservation (fill order) requests in order to return
// an OrderShipped or OrderDelayed response
class FillOrderAggregator(client: Client, purchase: Purchase, replyTo: ActorRef)
  extends Actor with ActorLogging {

  // Builder to keep track of the "fill level" of the Order (or in other words of the Purchase of the Client)
  var builder = new FillOrderResultBuilder(purchase)

  // Wait for the specified time on responses from the StockHouseServices.
  // We only terminate the ephemeral child after this timeout has passed instead of immediately once the order can be
  // filled, since we send RestoreOrders to late responders to unreserve their stock again.
  context.setReceiveTimeout(5.seconds)

  // Normal aggregation behavior (until Order is fulfilled)
  def receiveStockResult: Receive = {
    case ManagerEvent(_, InStock(stockHouseName : String, availableItems : Map[Product, Int])) =>
      builder.addItems(sender, stockHouseName, availableItems) // items were reserved
    case ManagerRejection(purchaseID, reason) =>
      log.error(s"An error occurred while processing purchase $purchaseID: $reason")
    case ReceiveTimeout => {
      // send restore orders to all StockHouseServices that have reserved stock so far (since the order has to be delayed)
      sendRestoreOrders(builder.stockHouseAvailabilities)
      replyTo ! OrderDelayed(client, purchase)

      // checking for completeness is no longer needed (since we already sent an OrderDelayed message),
      // just send RestoreOrders from now on
      context.become(receiveLateStockResult)
    }
  }

  // Aggregator behavior once Order is fulfilled
  // All late responders get a message sent back to unreserve their stock again (since the Order is already filled)
  def receiveLateStockResult : Receive = {
    case ManagerEvent(purchaseID, InStock(_, availableItems : Map[Product, Int])) => {
      // late InStock messages, just send back a restoration message
      sender ! ManagerCommand(RestoreOrder(availableItems), purchaseID, self)
    }
    case ManagerRejection(purchaseID, reason) =>
      log.error(s"An error occurred while processing purchase $purchaseID: $reason")
    case ReceiveTimeout =>
      // the task of this aggregator is done, so he can stop himself
      // if there are any more late responders, their reservations are expected to be fixed
      // when the goods have to actually be shipped at a later stage instead of now
      // when the goods are just being reserved for shipment
      context.stop(self)
  }

  // Behavior for checking if the order is complete
  // This completeness check happens after every message that is received during the "normal" unfulfilled order behavior
  def checkComplete : Receive = {
    case _ =>
      if (builder.isComplete) {
        // Both the shipped goods and any surpluses are returned once the builder is complete
        // A surplus can exist because each StockHouse checks all available products and a product can be
        // available in multiple StockHouses
        val (shippedGoods, surplus) = builder.result()

        // let the requester know the Order has been shipped
        replyTo ! OrderShipped(client, purchase, shippedGoods)

        // let the StockHouses know what should be unreserved again
        // (i.e. make the surplus of reserved items available again)
        sendRestoreOrders(surplus)

        // checking for completeness is no longer needed, just send RestoreOrders from now on
        context.become(receiveLateStockResult)

        // The task of this aggregator is not done yet, we will stay alive until the TimeOut happens to wait
        // for any late InStock messages.
        // Alternatively, we could have used a (modified) business handshake pattern for even more reliability,
        // but this seemed excessive for mere reservations (and not actual shipments).
      }
  }

  /**
   * Send RestoreOrders to all StockHouseServices with the stock they reserved so far.
   *
   * @param restore Map containing per StockHouseService ActorRef the items they have reserved
   *                (and should be unreserved again)
   */
  def sendRestoreOrders(restore : Map[ActorRef, Map[Product, Int]]): Unit = {
    restore foreach {
      case (stockHouseServiceRef, productsToRestore) =>
        // The ephemeral child is given as ReplyTo address, but we actually expect no replies to be sent for RestoreOrders.
        stockHouseServiceRef ! ManagerCommand(RestoreOrder(productsToRestore), purchase.id, self)
    }
  }

  // Initial behavior is to receive InStock messages and check whether the Order can be filled
  def receive : Receive = receiveStockResult andThen checkComplete
}

// Builder to keep track of the "fill level" of an Order (or in other words of the Purchase of a Client)
class FillOrderResultBuilder(purchase: Purchase) {
  // Map to keep track of which StockHouseServices were able to reserve some of the specified items of the Purchase,
  // with as key the ActorRef of the StockHouseService and as value the items that were reserved in its StockHouse.
  var stockHouseAvailabilities : Map[ActorRef, Map[Product, Int]] = Map.empty

  // We also keep a dictionary to know which StockHouse corresponds to which StockHouseService.
  // This is both used to prevent multiple responses for the same StockHouse and for testing purposes,
  // i.e. being able to print where goods are coming from in the ClientService.
  var stockHouseNames : Map[String, ActorRef] = Map.empty

  // Keep track of how much of the given Purchase remains to be fulfilled.
  // Once this is empty, the Purchase (or Order) can be fulfilled.
  var remainingPurchase : Purchase = purchase.copy()

  /**
   * Check whether we already received a response for the StockHouse with the given name,
   * with StockHouse names being expected to be unique.
   *
   * Every StockHouseService should give only one response (and therefore only be added once).
   * This is implemented as a safety measure, but is unneeded under normal circumstances.
   *
   * @param stockHouseName Name of the StockHouse we should check previous responses for
   */
  def handled(stockHouseName: String) : Boolean = {
    stockHouseNames.contains(stockHouseName)
  }

  /**
   * Keep track of which StockHouseService has let us know that their StockHouse reserved items for an order.
   * Also update the "fill level" of the order
   * (or in other words remove items from the Purchase containing the remaining goods).
   *
   * @param stockHouseServiceRef The StockHouseService's ActorRef through which items were reserved.
   * @param stockHouseName The name of the StockHouse where items were reserved.
   * @param availableItems The items that have been reserved in the specified StockHouse.
   */
  def addItems(stockHouseServiceRef: ActorRef, stockHouseName : String, availableItems: Map[Product, Int]): Unit = {
    // only handle every response once (only one response per actor is expected)
    if (!handled(stockHouseName)) {
      // update the "fill level" of the order (i.e. how many items remain to be reserved)
      remainingPurchase = remainingPurchase.removeItems(availableItems)
      // keep track of the StockHouseService through which the stock was reserved
      stockHouseAvailabilities = stockHouseAvailabilities.updated(stockHouseServiceRef, availableItems)
      // keep track of the StockHouse where items were reserved
      stockHouseNames = stockHouseNames.updated(stockHouseName, stockHouseServiceRef)
    }
  }

  // check whether the Order has been filled
  def isComplete: Boolean = {
    // if the Purchase has no items left to handle, the Order is filled
    remainingPurchase.items.isEmpty
  }

  /**
   * The result is a Tuple with the first element specifying which stock is actually used
   * and the second element specifying which stock was available, but unused (i.e. the surplus that should be
   * restored in the StockHouses since we already used those products from somewhere else)
   *
   * Remark:
   * Currently we do not perform any optimization when it comes to shipping goods.
   * In practice, it would be preferred to keep the number of StockHouses that have to send goods as low as possible
   * or to still prioritize sending goods from the closest StockHouse first.
   * However, this is a search problem that has very little to do with micro-service architectures so it
   * is considered to be out-of-scope for the assignment and has therefore not been done.
   * We will simply send the products from the first StockHouse(s) we find them in without optimizing other objectives.
   */
  def result() : (List[StockHouseShipment], Map[ActorRef, Map[Product, Int]]) = {
    // reserved items that we will actually use for our shipment (with their StockHouse name as key)
    var serviceUsage : Map[String, Map[Product, Int]] = Map.empty
    // reserved items that we will not end up using (since they have already been provided by another StockHouse)
    var serviceSurplus : Map[ActorRef, Map[Product, Int]] = Map.empty
    // remaining Purchase to fill (initially a copy of the full Order/Purchase),
    // we cannot reuse the remainingPurchase field since that one has already been emptied
    // (and is merely used to check whether the Order can be filled while here we will actually fill the order)
    var toFill : Purchase = purchase.copy()

    // for every StockHouseService that responded with its available products...
    stockHouseNames foreach {
      case (stockHouseName, stockHouseServiceRef) =>
        stockHouseAvailabilities.get(stockHouseServiceRef) foreach {
          productAvailabilities =>
            // ...check which requested products can be delivered through this StockHouseService ...
            productAvailabilities foreach {
              case (product, quantity) if (quantity > 0) => {
                // ... and if we still need some items of this product...
                val remainingQuantity: Int = toFill.getQuantity(product) // check how much we still need of this product
                if (remainingQuantity > 0) {
                  // ... compute the quantity that we can ship from this StockHouse...
                  val quantityToShip = if (remainingQuantity >= quantity) quantity else remainingQuantity
                  toFill = toFill.removeItem(product, quantityToShip)
                  // ... and keep track of what will be shipped from this StockHouse..
                  val currentAvailability = serviceUsage.getOrElse(stockHouseName, Map.empty)
                  serviceUsage =
                    serviceUsage.updated(stockHouseName, currentAvailability.updated(product, quantityToShip))

                  // ... and what is a reserved surplus that should be restored via this StockHouseService
                  //     (in case we only needed some items of this type from the entire reservation).
                  if (remainingQuantity < quantity) {
                    val quantitySurplus = quantity - remainingQuantity
                    val currentSurplus = serviceSurplus.getOrElse(stockHouseServiceRef, Map.empty)
                    serviceSurplus =
                      serviceSurplus.updated(stockHouseServiceRef, currentSurplus.updated(product, quantitySurplus))
                  }
                }
                else {
                  // ... and if we need no items of this product type anymore,
                  //     keep track of this reserved surplus for this StockHouseService
                  val currentSurplus = serviceSurplus.getOrElse(stockHouseServiceRef, Map.empty)
                  serviceSurplus =
                    serviceSurplus.updated(stockHouseServiceRef, currentSurplus.updated(product, quantity))
                }
              }
              case _ => // nothing has to be done if quantity <= 0 (as the StockHouse has not reserved this product then)
            }
        }
    }

    // Create a List of StockHouseShipments that are ready to be shipped.
    // We use the name of the StockHouses in the shipments to inform the Client where his goods will be coming from.
    val stockHouseShipments = serviceUsage.map {
      case (stockHouseName, products) =>
        StockHouseShipment(stockHouseName, products)
    }.toList

    // Return which StockHouses will ship which products and which StockHouseServices should be contacted
    // to restore which surpluses
    (stockHouseShipments, serviceSurplus)
  }
}