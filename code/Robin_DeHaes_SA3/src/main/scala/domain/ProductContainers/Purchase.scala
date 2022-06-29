package domain.ProductContainers

import domain.Product

import java.util.concurrent.atomic.AtomicLong

// A Purchase with a unique ID consisting of a number of products and their quantity
// The ID currently is always a String version of a Long, but other (unique) String IDs could be used as well.
case class Purchase(id : String = Purchase.generateID(), items: Map[Product, Int] = Map.empty) extends ProductContainer(items) {
  type Container = Purchase // Purchase is the actual items container

  // id always remains the same
  override def copy(items: Map[Product, Int] = items): Container = Purchase(id, items)

  /**
   * Remove a map of items and their respective quantities from the Purchase.
   *
   * REMARK: This method could perhaps be moved to ProductContainer, but it is currently only used in Purchase
   *         so it is kept here.
   *
   * @param itemsToRemove Map with (Product, Quantity) entries to remove
   */
  def removeItems(itemsToRemove: Map[Product, Int]): Purchase = {
    var purchase: Purchase = copy()
    for ((product, count) <- itemsToRemove) {
      purchase = purchase.removeItem(product, count)
    }
    purchase
  }

  override def toString: String = {
    s"Purchase $id\n" + super.toString
  }
}

object Purchase {
  // use an AtomicLong as counter to generate unique IDs (concurrently)
  val counter = new AtomicLong

  /**
   * Generate a new (unique) ID.
   */
  def generateID() : String = {
    counter.getAndIncrement().toString
  }
}