package services

import akka.actor.{Actor, ActorLogging, Props}
import domain.ProductContainers.{Purchase, StockHouse}
import domain.{Client, Product}
import services.messages.stockhouse._

// Object with necessary information to easily create StockHouseServices
object StockHouseService {
  def props(stockHouse: StockHouse): Props = Props(new StockHouseService(stockHouse))
  def name(stockHouse: StockHouse): String = stockHouse.name
}

// DOMAIN OBJECT PATTERN:
// StockHouseService maintains a reference to the current state of a StockHouse, which is the domain object.
// It receives Manager Messages that contain a Domain Message for modifying or querying
// the StockHouse (and will possibly also respond with a Manager Message).
// In addition to the Domain Message, the Manager Messages contain an id and replyTo reference for message deduplication.
// The referenced StockHouse will only handle business concerns, i.e. "execute the Domain Messages".
class StockHouseService(var stockHouse: StockHouse) extends Actor with ActorLogging {
  def receive: Receive = {
    case ManagerQuery(cmd, id, replyTo) => {
      try {
        val result = cmd match {
          // query the distance to the given client
          case DistanceTo(client: Client) =>
            DistanceToResult(client, stockHouse.distanceTo(client.address))
        }
        // return the query result
        replyTo ! ManagerResult(id, result)
      } catch {
        case ex: IllegalArgumentException =>
          // return the thrown exception
          replyTo ! ManagerRejection(id, ex.getMessage)
      }
    }
    case ManagerCommand(cmd, id, replyTo) => {
      try {
        cmd match {
          // modify the StockHouse's items
          case FillOrder(purchase : Purchase) => {
            val (updatedStockHouse, reservedItems) = stockHouse.reserveItems(purchase.items)
            stockHouse = updatedStockHouse

            // let the replyTo know what Stock was available (and is reserved)
            replyTo ! ManagerEvent(id, InStock(stockHouse.name, reservedItems))
          }
          // restore the surplus stock that should not be shipped in the end
          // (due to the order being delayed or having sufficient stock already)
          case RestoreOrder(goodsToRestore : Map[Product, Int]) => {
            stockHouse = stockHouse.unreserveItems(goodsToRestore)
            // currently stock restorations don't send an event back
            // (since it is seen as a "background operation"
            //  and the ephemeral child that sent them will already be dead in most cases anyway)
          }
        }
      }
      catch {
        case ex: IllegalArgumentException =>
          // return the thrown exception
          replyTo ! ManagerRejection(id, ex.getMessage)
      }
    }
  }
}