package services

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import domain.ProductContainers.Purchase
import domain._
import services.messages.orderprocessing.{OrderDelayed, OrderShipped, PurchaseConfirmed}
import services.messages.shoppingcart.{AddToCart, FinalizePurchase, RemoveFromCart}

// Object with necessary information to easily create ClientServices
object ClientService {
  def props(client: Client, processingService: ActorRef): Props =
    Props(new ClientService(client, processingService))
  def name(client: Client): String = client.name
}

// ClientService handles the communication between a Client and a ProcessingService.
class ClientService(client: Client, processingService: ActorRef) extends Actor with ActorLogging {
  // A Purchase represents the current state of the "shopping cart" and takes care of the business logic.
  def shoppingCart(purchase: Purchase) : Receive = {
    case AddToCart(item: Product, qty: Int) =>
      // add items to your Purchase (the purchase is not finalized until a MakePurchase message is received)
      context.become(shoppingCart(purchase.addItem(item, qty)))
    case RemoveFromCart(item: Product, qty: Int) =>
      // remove items from your Purchase (the purchase is not finalized until a MakePurchase message is received)
      context.become(shoppingCart(purchase.removeItem(item, qty)))
    case FinalizePurchase =>
      if (!purchase.isEmpty()) {
        // confirm your current Purchase (i.e. make the actual Purchase)
        processingService ! PurchaseConfirmed(client, purchase)

        // wait for your order to be processed
        context.become(waitForOrder)
      } else {
        log.warning("Unable to make an empty purchase!")
      }
  }

  // Wait for your confirmed Purchase to finish processing
  def waitForOrder : Receive = {
    case OrderShipped(client, purchase, shippedGoods) =>
      log.info(s"Thank you for ordering ${if(client.hasPrime) "with PRIME" else ""}, ${client.name}!\n"+
        s"Your purchase :\n$purchase\n" +
        s"Your shipments :\n${shippedGoods.mkString("\n")}")

      // you can make new Purchases now
      context.become(shoppingCart(Purchase()))
    case OrderDelayed(client, purchase) =>
      log.info(s"We are sorry ${if(client.hasPrime) "PRIME subscriber " else ""}${client.name}, " +
        s"your purchase cannot be fulfilled right now : \n"+
        s"$purchase\nPlease try again later.")

      // your previous "shopping cart" is restored, so you can easily try again later
      context.become(shoppingCart(purchase))
  }

  // initially start of with an empty "shopping cart"
  def receive: Receive = shoppingCart(Purchase())
}