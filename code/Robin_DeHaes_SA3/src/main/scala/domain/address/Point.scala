package domain.address

// Class representing an address as a Cartesian point with an x- and y-coordinate
case class Point(x: Int, y: Int) {

  /**
   * Calculate the distance from this Point to another Point.
   *
   * @param point point to calculate the distance to
   */
  def distanceTo(point : Point) : Double = {
    math.sqrt(math.pow(x - point.x, 2) + math.pow(y - point.y, 2))
  }
}