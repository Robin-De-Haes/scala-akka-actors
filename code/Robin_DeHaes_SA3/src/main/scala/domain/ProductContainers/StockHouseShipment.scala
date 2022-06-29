package domain.ProductContainers

import domain.Product

// Helper class used in messages to wrap together the name of a StockHouse and the items that will be shipped from
// that StockHouse.
// This class has mainly been added for testing purposes to be able to identify which StockHouses will send
// which items to a Client to fulfill a Purchase.
case class StockHouseShipment(stockHouseName: String, items: Map[Product, Int] = Map.empty) extends ProductContainer(items) {
  type Container = StockHouseShipment // StockHouseShipment is the actual items container

  override def copy(items: Map[Product, Int]): StockHouseShipment = StockHouseShipment(stockHouseName, items)

  override def toString: String = {
    s"From $stockHouseName\n" + super.toString
  }
}
