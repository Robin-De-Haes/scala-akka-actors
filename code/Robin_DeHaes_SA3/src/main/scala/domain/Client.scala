package domain

import domain.address.Point

// Class representing a Client with a specified name and address.
// The field hasPrime is true if the Client is subscribed to Prime, so for a normal (default) Client it is false.
case class Client(name: String, address: Point, hasPrime: Boolean = false)