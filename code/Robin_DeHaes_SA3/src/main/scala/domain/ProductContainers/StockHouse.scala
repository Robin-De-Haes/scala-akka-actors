package domain.ProductContainers

import domain.Product
import domain.address.Point

// Class representing a stock house with a name, address (as a Cartesian point)
// and the stock it contains (Map of products and their quantity)
case class StockHouse(name: String, address: Point, items: Map[Product, Int] = Map.empty) extends ProductContainer(items) {
  type Container = StockHouse // StockHouse is the actual items container

  // name and address always remain the same
  override def copy(items: Map[Product, Int] = items): Container = StockHouse(name, address, items)

  /**
   * Reserves items, which means these products are made unavailable (i.e. removed from stock).
   * If items are not sufficiently available, only what is available is reserved.
   * The modified StockHouse and the actually reserved items are returned as a tuple.
   *
   * @param itemsToReserve Products and their amount that needs to reserved
   */
  def reserveItems(itemsToReserve: Map[Product, Int]): (StockHouse, Map[Product, Int]) = {
    var updatedStockHouse = copy()

    // return the maximum available products if less than the requested amount is available
    val reservedItems = itemsToReserve.transform((product, quantity) => {
      val availableQuantity = getQuantity(product)
      if (availableQuantity > quantity) {
        // full reservation possible
        updatedStockHouse = updatedStockHouse.removeItem(product, quantity) // remove the requested quantity from the stock
        quantity
      }
      else {
        // no full reservation is possible, only reserve what is available
        updatedStockHouse = removeItem(product, availableQuantity) // remove the quantity that is actually available from the stock
        availableQuantity
      }
    })

    // return both the updated StockHouse and the goods that were actually reserved
    (updatedStockHouse, reservedItems)
  }

  /**
   * Unreserve a map of items and their respective quantities in the StockHouse,
   * i.e. add the items back to the stock.
   *
   * @param itemsToUnreserve Map with (Product, Quantity) entries to unreserve
   */
  def unreserveItems(itemsToUnreserve: Map[Product, Int]): StockHouse = {
    var stockHouse: StockHouse = copy()
    for ((product, count) <- itemsToUnreserve) {
      stockHouse = stockHouse.addItem(product, count)
    }
    stockHouse
  }

  /**
   * Calculate the distance from this StockHouse to a given Point.
   *
   * @param toAddress Point to calculate the distance to
   */
  def distanceTo(toAddress: Point): Double = {
    address.distanceTo(toAddress)
  }

  override def toString: String = {
    s"Stock house $name ($Point):\n" + super.toString
  }
}
