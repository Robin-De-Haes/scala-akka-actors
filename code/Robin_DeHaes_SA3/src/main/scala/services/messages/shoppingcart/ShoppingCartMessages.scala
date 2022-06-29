package services.messages.shoppingcart

import domain.Product

// Messages for adding, removing and finalizing a shopping cart (sent to a ClientService)
case class AddToCart(product: Product, qty: Int)
case class RemoveFromCart(product: Product, qty: Int)
case object FinalizePurchase // finalize shopping cart