package services.messages.stockhouse

import domain.ProductContainers.Purchase
import domain.{Client, Product}

// Domain Messages used for the domain object (StockHouse) in the DOMAIN OBJECT PATTERN

// commands
sealed trait Command
case class FillOrder(purchase: Purchase) extends Command // reserve goods
case class RestoreOrder(orderToRestore : Map[Product, Int]) extends Command // unreserve goods

// events
sealed trait Event
case class InStock(stockHouseName : String, availableItems : Map[Product, Int]) extends Event // goods were reserved

// queries
sealed trait Query
case class DistanceTo(client: Client) extends Query // distance to a Client is requested (no modification involved)

// results
sealed trait Result
case class DistanceToResult(client : Client, distance: Double) extends Result // result of distance query