package services.helpers

import akka.actor.ActorRef
import domain.Client
import domain.ProductContainers.Purchase

import scala.collection.immutable.Queue

// Helper class to have both a (Boolean) priority queue and FIFO behavior.
// PrimePriorityQueue internally uses 2 separate queues that both have FIFO behavior,
// while dequeue will always take an element from the prioritized primeQueue first to have PriorityQueue behavior.
// Alternatively, we could have used the built-in PriorityQueue by first prioritizing on hasPrime and then on a timestamp
// that we added to queue entries, but our approach supports our functional programming style
// while the built-in Scala PriorityQueue is mutable.
class PrimePriorityQueue {
  // Queue for Prime subscribers
  var primeQueue : Queue[(Client, Purchase, ActorRef)] = Queue.empty
  // Queue for "regular" subscribers
  var regularQueue : Queue[(Client, Purchase, ActorRef)] = Queue.empty

  /**
   * Enqueue an element.
   * If the Client embedded in the element has hasPrime = true, it will be added to the primeQueue.
   * Otherwise, it will be added to the regularQueue.
   *
   * @param element element to enqueue
   */
  def enqueue(element : (Client, Purchase, ActorRef)): Unit = {
    if (element._1.hasPrime) primeQueue = primeQueue.enqueue(element)
    else regularQueue = regularQueue.enqueue(element)
  }

  /**
   * Dequeue an element.
   * If the primeQueue has entries, these will be dequeued first.
   */
  def dequeue() : (Client, Purchase, ActorRef) = {
    if (primeQueue.nonEmpty) {
      val (element, newQueue) = primeQueue.dequeue
      primeQueue = newQueue
      element
    }
    else {
      val (element, newQueue) = regularQueue.dequeue
      regularQueue = newQueue
      element
    }
  }

  def isEmpty: Boolean = {
    primeQueue.isEmpty && regularQueue.isEmpty
  }
}