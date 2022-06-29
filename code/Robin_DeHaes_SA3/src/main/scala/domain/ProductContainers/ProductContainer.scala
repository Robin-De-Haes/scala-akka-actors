package domain.ProductContainers

import domain.Product

// Abstract class with some common methods for manipulating a collection of products.
// Purchase, StockHouse and StockHouseShipment (which handle collections of Products) extend this class.
abstract class ProductContainer(items: Map[Product, Int]) {
  // return type for methods (since we're mostly using a functional approach), concretized in subclasses
  type Container <: ProductContainer

  // subclasses should provide a valid copy method
  def copy(items: Map[Product, Int] = items) : Container

  /**
   * Check if the contained collection of Products is empty.
   */
  def isEmpty() : Boolean = items.isEmpty

  /**
   * Add item to the ProductContainer.
   *
   * @param item product to add
   * @param count quantity of that product to add
   */
  def addItem(item: Product, count: Int) : Container = {
    // design by contract (count >= 0)
    require(count >= 0, s"count cannot be negative, but received $count for $item")

    // add or update the count for the product
    val currentCount = getQuantity(item)
    copy(items = items.updated(item, currentCount + count))
  }

  /**
   * Remove item from the ProductContainer.
   *
   * @param item product to remove
   * @param count quantity of that product to remove
   */
  def removeItem(item: Product, count: Int) : Container = {
    // design by contract (count >= 0)
    require(count >= 0, s"count cannot be negative, but received $count for $item")

    val currentCount = items.getOrElse(item, 0)
    val newCount = currentCount - count
    if (newCount <= 0) copy(items = items - item) // all items of that product are removed
    else copy(items = items.updated(item, newCount)) // some items of the product are removed
  }

  /**
   * Get the current quantity of the specified item that is present in the ProductContainer.
   *
   * @param item product to get the quantity from
   */
  def getQuantity(item: Product) : Int = {
    items.getOrElse(item, 0)
  }

  override def toString: String = {
    items.map {
      case (product, quantity) => s"$quantity x ${product.name}"
    }.mkString("\n")
  }
}