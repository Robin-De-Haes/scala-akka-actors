package domain

// Class representing a product that can be bought (identified by name).
// This class currently serves as a type-safe wrapper for String names.
// The name is expected to be unique, but this is not explicitly enforced.
case class Product(name: String)