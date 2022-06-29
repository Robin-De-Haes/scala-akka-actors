package services.messages.orderprocessing

import akka.actor.ActorRef
import domain.Client
import domain.ProductContainers.{Purchase, StockHouseShipment}

// Messages for starting or finishing the processing of an order
// the order can be processed
case class PurchaseConfirmed(client: Client, purchase: Purchase)
// the order is successfully completed
case class OrderShipped(client: Client, purchase: Purchase, shippedGoods: List[StockHouseShipment])
// the order could not be completed
case class OrderDelayed(client : Client, purchase : Purchase)

// Messages sent during order processing to indicate which StockHouseServices should be contacted
// to communicate with the nearest StockHouses.
// Purchase is sent in case we would want to use the Purchase information to already filter out StockHouses
// (in a future extension), although this currently does not happen.
case class NearestStockHouses(client: Client, purchase : Purchase, nearestStockHouseServiceRefs: List[ActorRef])


