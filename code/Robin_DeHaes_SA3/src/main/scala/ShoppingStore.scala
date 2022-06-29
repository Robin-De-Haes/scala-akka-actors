import akka.actor.{ActorRef, ActorSystem}
import domain.ProductContainers.StockHouse
import domain.address.Point
import domain.{Client, Product, ProductContainers}
import services.messages.shoppingcart.{AddToCart, FinalizePurchase, RemoveFromCart}
import services.{ClientService, ProcessingService, StockHouseService}

object ShoppingStore extends App {

  // Five test scenario's have been setup to show some functionalities, each scenario will reinitialize an ActorSystem
  // and restore the StockHouses' stock before execution (so they won't influence each other).
  // A simple Thread Sleep is used to give an ample amount of time for the scenario to finish (before terminating the ActorSystem).
  // Alternatively, we could have sent a message back from the ClientService but this seemed redundant (and code pollution).

  // Please uncomment and execute the scenario that you want to test (at the bottom of this file).
  // Some (actor) logging information will be written during each test.

  /* Setting up the basic instances used when testing */

  // number of StockHouses to check availability in
  val numberOfStockHousesToCheck : Int = 5

  // create 5 Clients
  val clientCoen : Client = Client("Coen", Point(2,3))
  val clientCamilo : Client = Client("Camilo", Point(5,1), hasPrime = true)
  val clientAhmed : Client = Client("Ahmed", Point(3,4), hasPrime = true)
  val clientRobin : Client = Client("Robin", Point(3,6))
  val clientLuna : Client = Client("Luna", Point(2,8), hasPrime = true)

  // create 8 Products
  val godOfWarGame : Product = Product("God of War")
  val falloutGame : Product = Product("Fallout")
  val rocketLeagueGame : Product = Product("Rocket League")
  val watchmenVideo : Product = Product("Watchmen")
  val futuramaVideo : Product = Product("Futurama")
  val rickAndMortyVideo : Product = Product("Rick and Morty")
  val theRingVideo : Product = Product("Ringu")
  val scalaBook: Product = Product("Programming in Scala")

  /**
   * Helper method to generate new, fully filled StockHouses and their respective StockHouseServices
   *
   * In total we have a stock of (excluding the stock from StockHouse 3 which is to far away to ship anything from):
   * - God of War: 35
   * - Fallout: 77
   * - Rocket League: 11
   * - Programming in Scala: 5
   * - Watchmen: 32
   * - Futurama: 35
   * - Rick and Morty: 35
   */
  def generateStockHouseServices(actorSystem: ActorSystem) : List[ActorRef] = {
    // create 6 StockHouses
    var stockHouse1 : StockHouse = ProductContainers.StockHouse("StockHouse1", Point(0,0))
    var stockHouse2 : StockHouse = ProductContainers.StockHouse("StockHouse2", Point(1,1))
    // StockHouse3 is a filled solitary StockHouse that should never show up in the logging
    // (as it's never one of the 5 nearest StockHouses)
    var stockHouse3 : StockHouse = ProductContainers.StockHouse("StockHouse3", Point(92,29))
    var stockHouse4 : StockHouse = ProductContainers.StockHouse("StockHouse4", Point(3,3))
    var stockHouse5 : StockHouse = ProductContainers.StockHouse("StockHouse5", Point(4,4))
    val stockHouse6 : StockHouse = ProductContainers.StockHouse("StockHouse6", Point(5,5))

    // add some stock to the StockHouses
    // StockHouse 1 only has games & books
    stockHouse1 = stockHouse1.addItem(godOfWarGame, 30)
    stockHouse1 = stockHouse1.addItem(falloutGame, 25)
    stockHouse1 = stockHouse1.addItem(rocketLeagueGame, 10)
    stockHouse1 = stockHouse1.addItem(scalaBook, 3)

    // StockHouse 2 only has videos
    stockHouse2 = stockHouse2.addItem(watchmenVideo, 30)
    stockHouse2 = stockHouse2.addItem(futuramaVideo, 25)
    stockHouse2 = stockHouse2.addItem(rickAndMortyVideo, 10)

    // StockHouse 3 has plenty of everything, but is too far from the client to ever be chosen for delivery
    stockHouse3 = stockHouse3.addItem(godOfWarGame, 100)
    stockHouse3 = stockHouse3.addItem(falloutGame, 100)
    stockHouse3 = stockHouse3.addItem(rocketLeagueGame, 100)
    stockHouse3 = stockHouse3.addItem(watchmenVideo, 100)
    stockHouse3 = stockHouse3.addItem(futuramaVideo, 100)
    stockHouse3 = stockHouse3.addItem(rickAndMortyVideo, 100)
    stockHouse3 = stockHouse3.addItem(scalaBook, 100)

    // StockHouse 4 has a limited amount of different products left
    stockHouse4 = stockHouse4.addItem(godOfWarGame, 5)
    stockHouse4 = stockHouse4.addItem(falloutGame, 2)
    stockHouse4 = stockHouse4.addItem(rocketLeagueGame, 1)
    stockHouse4 = stockHouse4.addItem(watchmenVideo, 2)
    stockHouse4 = stockHouse4.addItem(futuramaVideo, 10)
    stockHouse4 = stockHouse4.addItem(rickAndMortyVideo, 5)
    stockHouse4 = stockHouse4.addItem(scalaBook, 1)

    // StockHouse 5 only has specific games & videos & a book about Scala
    stockHouse5 = stockHouse5.addItem(falloutGame, 50)
    stockHouse5 = stockHouse5.addItem(rickAndMortyVideo, 20)
    stockHouse5 = stockHouse5.addItem(scalaBook, 1)

    // StockHouse 6 is currently completely out of stock

    // create a StockHouseService for each StockHouse
    val stockHouseService1 = actorSystem.actorOf(StockHouseService.props(stockHouse1), StockHouseService.name(stockHouse1))
    val stockHouseService2 = actorSystem.actorOf(StockHouseService.props(stockHouse2), StockHouseService.name(stockHouse2))
    val stockHouseService3 = actorSystem.actorOf(StockHouseService.props(stockHouse3), StockHouseService.name(stockHouse3))
    val stockHouseService4 = actorSystem.actorOf(StockHouseService.props(stockHouse4), StockHouseService.name(stockHouse4))
    val stockHouseService5 = actorSystem.actorOf(StockHouseService.props(stockHouse5), StockHouseService.name(stockHouse5))
    val stockHouseService6 = actorSystem.actorOf(StockHouseService.props(stockHouse6), StockHouseService.name(stockHouse6))

    // return the created StockHouseServices
    List[ActorRef](stockHouseService1, stockHouseService2, stockHouseService3,
      stockHouseService4, stockHouseService5, stockHouseService6)
  }

  def executeScenario1() = {
    // Initialize the Actor System used for our Shopping Store
    val actorSystem : ActorSystem = ActorSystem("ShoppingStore")

    // SCENARIO 1: One Client makes a purchase which can be fulfilled
    // Purchased (order of printing can be different due to Map storage):
    // 1 x Watchmen
    // 8 x God of War
    // 4 X Rocket League
    // 10 x Futurama
    // 13 x Rick and Morty
    // 1 x Programming in Scala
    val stockHouseServices = generateStockHouseServices(actorSystem)
    val processingService = actorSystem.actorOf(ProcessingService.props(stockHouseServices, numberOfStockHousesToCheck), ProcessingService.name())
    val clientServiceCoen = actorSystem.actorOf(ClientService.props(clientCoen, processingService), ClientService.name(clientCoen))

    clientServiceCoen ! AddToCart(watchmenVideo, 4)
    clientServiceCoen ! AddToCart(godOfWarGame, 10)
    clientServiceCoen ! AddToCart(rocketLeagueGame, 4)
    clientServiceCoen ! AddToCart(futuramaVideo, 10)
    clientServiceCoen ! AddToCart(rickAndMortyVideo, 13)
    clientServiceCoen ! AddToCart(scalaBook, 1)
    clientServiceCoen ! RemoveFromCart(godOfWarGame, 2)
    clientServiceCoen ! RemoveFromCart(watchmenVideo, 3)
    clientServiceCoen ! FinalizePurchase

    // wait 12 seconds for the test to finish
    Thread.sleep(12000)

    actorSystem.terminate()
  }

  def executeScenario2() = {
    // Initialize the Actor System used for our Shopping Store
    val actorSystem : ActorSystem = ActorSystem("ShoppingStore")

    // SCENARIO 2: One Client makes a purchase which can NOT be fulfilled (due to shortage of "The Ring" video)
    val stockHouseServices = generateStockHouseServices(actorSystem)
    val processingService = actorSystem.actorOf(ProcessingService.props(stockHouseServices, numberOfStockHousesToCheck), ProcessingService.name())
    val clientServiceCoen = actorSystem.actorOf(ClientService.props(clientCoen, processingService), ClientService.name(clientCoen))

    clientServiceCoen ! AddToCart(theRingVideo, 7)
    clientServiceCoen ! AddToCart(scalaBook, 1)
    clientServiceCoen ! FinalizePurchase

    // wait 15 seconds for the test to finish
    Thread.sleep(15000)

    actorSystem.terminate()
  }

  def executeScenario3() = {
    // Initialize the Actor System used for our Shopping Store
    val actorSystem : ActorSystem = ActorSystem("ShoppingStore")

    // SCENARIO 3: Two Clients make a purchase which can be fulfilled
    // Purchased by Coen (order of printing can be different due to Map storage):
    // 10 x Futurama
    // 13 x Rick and Morty
    // 1 x Programming in Scala
    // Purchased by Robin (order of printing can be different due to Map storage):
    // 4 x Programming in Scala
    // 4 X Rocket League
    val stockHouseServices = generateStockHouseServices(actorSystem)
    val processingService = actorSystem.actorOf(ProcessingService.props(stockHouseServices, numberOfStockHousesToCheck), ProcessingService.name())
    val clientServiceCoen = actorSystem.actorOf(ClientService.props(clientCoen, processingService), ClientService.name(clientCoen))
    val clientServiceRobin = actorSystem.actorOf(ClientService.props(clientRobin, processingService), ClientService.name(clientRobin))

    // Coen's Cart
    clientServiceCoen ! AddToCart(futuramaVideo, 10)
    clientServiceCoen ! AddToCart(rickAndMortyVideo, 13)
    clientServiceCoen ! AddToCart(scalaBook, 1)

    // Robin's Cart
    clientServiceRobin ! AddToCart(watchmenVideo, 4)
    clientServiceRobin ! AddToCart(scalaBook, 4)

    // Finalize the orders
    clientServiceCoen ! FinalizePurchase
    clientServiceRobin ! FinalizePurchase

    // wait 20 seconds for the test to finish
    Thread.sleep(20000)

    actorSystem.terminate()
  }

  def executeScenario4() = {
    // Initialize the Actor System used for our Shopping Store
    val actorSystem : ActorSystem = ActorSystem("ShoppingStore")

    // SCENARIO 4: Two Clients make a purchase of which only one can be fulfilled because of a shortage
    //             in "Programming in Scala" books
    // Purchased by Coen (order of printing can be different due to Map storage):
    // 10 x Futurama
    // 13 x Rick and Morty
    // 1 x Programming in Scala (SHORTAGE)
    // Purchased by Robin (order of printing can be different due to Map storage):
    // 5 x Programming in Scala (SHORTAGE)
    // 4 X Watchmen
    val stockHouseServices = generateStockHouseServices(actorSystem)
    val processingService = actorSystem.actorOf(ProcessingService.props(stockHouseServices, numberOfStockHousesToCheck), ProcessingService.name())
    val clientServiceCoen = actorSystem.actorOf(ClientService.props(clientCoen, processingService), ClientService.name(clientCoen))
    val clientServiceRobin = actorSystem.actorOf(ClientService.props(clientRobin, processingService), ClientService.name(clientRobin))

    // Coen's Cart
    clientServiceCoen ! AddToCart(futuramaVideo, 10)
    clientServiceCoen ! AddToCart(rickAndMortyVideo, 13)
    clientServiceCoen ! AddToCart(scalaBook, 1)

    // Robin's Cart
    clientServiceRobin ! AddToCart(watchmenVideo, 4)
    clientServiceRobin ! AddToCart(scalaBook, 5)

    // Finalize the orders
    clientServiceCoen ! FinalizePurchase
    clientServiceRobin ! FinalizePurchase

    // wait 20 seconds for the test to finish
    Thread.sleep(20000)

    actorSystem.terminate()
  }

  def executeScenario5() = {
    // Initialize the Actor System used for our Shopping Store
    val actorSystem : ActorSystem = ActorSystem("ShoppingStore")

    // SCENARIO 5: Five Clients make a purchase of which one cannot be fulfilled because of a shortage
    //             in "Programming in Scala" books and another because of a shortage in "The Ring" videos.
    //             Three clients are Prime subscribers and expected to be processed earlier (on average),
    //             even though their messages actually get sent last.
    //             In summary, PRIME subscriber Luna should have an unfulfilled order because of shortage in "The Ring"
    //             and one "normal" subscriber is expected to have an unfulfilled order because the PRIME subscribers
    //             reserved all the available "Programming in Scala" books. One "normal" subscriber might be processed first
    //             since its request will already be processing when the Prime requests arrive, but the other "normal"
    //             subscriber is expected to be processed last.
    val stockHouseServices = generateStockHouseServices(actorSystem)
    val processingService = actorSystem.actorOf(ProcessingService.props(stockHouseServices, numberOfStockHousesToCheck), ProcessingService.name())
    val clientServiceCoen = actorSystem.actorOf(ClientService.props(clientCoen, processingService), ClientService.name(clientCoen))
    val clientServiceCamilo = actorSystem.actorOf(ClientService.props(clientCamilo, processingService), ClientService.name(clientCamilo))
    val clientServiceAhmed = actorSystem.actorOf(ClientService.props(clientAhmed, processingService), ClientService.name(clientAhmed))
    val clientServiceRobin = actorSystem.actorOf(ClientService.props(clientRobin, processingService), ClientService.name(clientRobin))
    val clientServiceLuna = actorSystem.actorOf(ClientService.props(clientLuna, processingService), ClientService.name(clientLuna))

    // Coen's Cart
    clientServiceCoen ! AddToCart(futuramaVideo, 10)
    clientServiceCoen ! AddToCart(rickAndMortyVideo, 13)
    clientServiceCoen ! AddToCart(scalaBook, 1)

    // Robins Cart
    clientServiceRobin ! AddToCart(watchmenVideo, 4)
    clientServiceRobin ! AddToCart(scalaBook, 1)

    // Camilo's Cart
    clientServiceCamilo ! AddToCart(rickAndMortyVideo, 4)
    clientServiceCamilo ! AddToCart(scalaBook, 2)

    // Ahmed's Cart
    clientServiceAhmed! AddToCart(falloutGame, 4)
    clientServiceAhmed! AddToCart(scalaBook, 2)

    // Luna's Cart
    clientServiceLuna ! AddToCart(rocketLeagueGame, 4)
    clientServiceLuna ! AddToCart(theRingVideo, 1)

    // Finalize the orders
    clientServiceCoen ! FinalizePurchase
    clientServiceRobin ! FinalizePurchase
    clientServiceCamilo ! FinalizePurchase
    clientServiceLuna ! FinalizePurchase
    clientServiceAhmed! FinalizePurchase

    // wait 30 seconds for the test to finish
    Thread.sleep(30000)

    actorSystem.terminate()
  }

  // SCENARIO 1: One Client makes a large purchase, containing 6 different products, which can be fulfilled
  // Purchased (order of printing can be different due to Map storage):
  // 1 x Watchmen
  // 8 x God of War
  // 4 X Rocket League
  // 10 x Futurama
  // 13 x Rick and Morty
  // 1 x Programming in Scala

  executeScenario1()

  // SCENARIO 2: One Client makes a purchase which can NOT be fulfilled (due to shortage of "The Ring" video)

  // executeScenario2()

  // SCENARIO 3: Two Clients make a purchase which can be fulfilled
  // Purchased by Coen (order of printing can be different due to Map storage):
  // 10 x Futurama
  // 13 x Rick and Morty
  // 1 x Programming in Scala
  // Purchased by Camilo (order of printing can be different due to Map storage):
  // 4 x Programming in Scala
  // 4 X Rocket League

  // executeScenario3()

  // SCENARIO 4: Two Clients make a purchase of which only one can be fulfilled because of a shortage
  //             in "Programming in Scala" books
  // Purchased by Coen (order of printing can be different due to Map storage):
  // 10 x Futurama
  // 13 x Rick and Morty
  // 1 x Programming in Scala (SHORTAGE)
  // Purchased by Robin (order of printing can be different due to Map storage):
  // 5 x Programming in Scala (SHORTAGE)
  // 4 X Watchmen

  // executeScenario4()

  // SCENARIO 5: Five Clients make a purchase of which one cannot be fulfilled because of a shortage
  //             in "Programming in Scala" books and another because of a shortage in "The Ring" video's.
  //             Three clients are Prime subscribers and expected to be processed earlier (on average),
  //             even though their messages actually get sent last.
  //             In summary, PRIME subscriber Luna should have an unfulfilled order because of shortage in "The Ring"
  //             and one "normal" subscriber is expected to have an unfulfilled order because the PRIME subscribers
  //             reserved all the available "Programming in Scala" books. One "normal" subscriber might be processed first
  //             since its request will already be processing when the Prime requests arrive, but the other "normal"
  //             subscriber is expected to be processed last.

  // executeScenario5()
}
