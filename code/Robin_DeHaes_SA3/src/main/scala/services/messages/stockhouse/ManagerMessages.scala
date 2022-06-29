package services.messages.stockhouse

import akka.actor.ActorRef

// Communication-related Manager Messages delegated by the manager (StockHouseService)
// to the domain object (StockHouse) in the DOMAIN OBJECT PATTERN

case class ManagerCommand(cmd: Command, id: String, replyTo: ActorRef)
case class ManagerEvent(id: String, event: Event)
case class ManagerQuery(cmd: Query, id: String, replyTo: ActorRef)
case class ManagerResult(id: String, result: Result)
case class ManagerRejection(id: String, reason: String) // handle exceptions that occurred during querying or modifying