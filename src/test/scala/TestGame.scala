import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Props
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.testkit.TestActorRef
import com.github.kerzok.Model.{CreateGameRequest, CreateGameResponse, JoinGameRequest, JoinGameResponse}
import com.github.kerzok.Utils.GameSide
import com.github.kerzok.Utils.JsonSupport._
import com.github.kerzok._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * Created by kerzo on 11.10.2016.
  */
class TestGame extends WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest {
  val host = "localhost"
  val port = 8080
  val gameServer = TestActorRef[GameManager](Props(classOf[GameManager], host, port))
  val wsClient = WSProbe()
  val route = gameServer.underlyingActor.route
  val wsRoute = gameServer.underlyingActor.wsRoute

  override def afterAll(): Unit = {
    Thread.sleep(10000)
    val result = system.terminate()
    Await.result(result, 17 seconds)
  }

  "An game server" should {
    "respond with game id after request for startGame" in {
      Post("/createGame", CreateGameRequest(GameSide.Tic)) ~> gameServer.underlyingActor.route ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[CreateGameResponse]
        response.status shouldEqual "OK"
        response.url.isDefined shouldEqual true
        response.gameId.isDefined shouldEqual true
        response.url.get shouldEqual s"ws://$host:${port + 1}/game/${response.gameId.get}:${GameSide.Tic}"
      }
    }
    "respond with game id after request for joinGame" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Toe)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      Post("/joinGame", JoinGameRequest(game.id)) ~> gameServer.underlyingActor.route ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JoinGameResponse]
        response.status shouldEqual "OK"
        response.url.isDefined shouldEqual true
        response.url.get shouldEqual s"ws://$host:${port + 1}/game/${game.id}:${GameSide.Tic}"
      }
    }
    "reject with MalformedRequestContentRejection after request for createGame with invalid side" in {
      Post("/createGame").withEntity(ContentTypes.`application/json`, "{\"side\":\"blah\"}") ~> gameServer.underlyingActor.route ~> check {
        rejections.exists(elem => elem.isInstanceOf[MalformedRequestContentRejection]) shouldBe true
      }
    }
    "respond with error after request for joinGame with invalid game id" in {
      Post("/joinGame", JoinGameRequest("")) ~> gameServer.underlyingActor.route ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JoinGameResponse]
        response.status shouldEqual "Fail"
        response.url.isDefined shouldEqual false
        response.errorMessage.isDefined shouldEqual true
        response.errorMessage.get shouldEqual "There is no game with such id"
      }
    }
    "connect to websocket" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Toe)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val url = game.connectionUrl(host, port, isFirst = true)
      WS(s"/game/${game.id}:${GameSide.Toe}", wsClient.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
      }
    }
    "block connection if third user tried to connect" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Tic)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val firstPlayer = WSProbe()
      val secondPlayer = WSProbe()
      val thirdPlayer = WSProbe()
      WS(s"/game/${game.id}:${GameSide.Tic}", firstPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
        WS(s"/game/${game.id}:${GameSide.Toe}", secondPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
          isWebSocketUpgrade shouldEqual true
          WS(s"/game/${game.id}:${GameSide.Toe}", thirdPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
            isWebSocketUpgrade shouldEqual false
          }
        }
      }
    }
    "send fail message on move twice" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Tic)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val firstPlayer = WSProbe()
      val secondPlayer = WSProbe()
      WS(s"/game/${game.id}:${GameSide.Tic}", firstPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
        WS(s"/game/${game.id}:${GameSide.Toe}", secondPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
          pushEvent(GameStart, waiter = Seq(firstPlayer, secondPlayer))
          pushEvent(Move(GameSide.Tic, 0, 0), Seq(firstPlayer), Seq(secondPlayer))
          val stringMove = GameEvent.eventToJson(Move(GameSide.Tic, 0, 0))
          val errorMessage = GameEvent.eventToJson(Error("Not your turn"))
          firstPlayer.sendMessage(stringMove)
          firstPlayer.expectMessage(errorMessage)
          pushEvent(Move(GameSide.Toe, 1, 0), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 1, 1), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 1, 2), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 2, 2), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Win(GameSide.Tic), waiter = Seq(firstPlayer, secondPlayer))
          firstPlayer.sendCompletion()
          firstPlayer.expectCompletion()
          secondPlayer.sendCompletion()
          secondPlayer.expectCompletion()
        }
      }
    }
    "send fail message on move with wrong coordinates" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Tic)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val firstPlayer = WSProbe()
      val secondPlayer = WSProbe()
      WS(s"/game/${game.id}:${GameSide.Tic}", firstPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
        WS(s"/game/${game.id}:${GameSide.Toe}", secondPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
          pushEvent(GameStart, waiter = Seq(firstPlayer, secondPlayer))
          pushEvent(Move(GameSide.Tic, 0, 0), Seq(firstPlayer), Seq(secondPlayer))
          val stringMove = GameEvent.eventToJson(Move(GameSide.Toe, 5, 5))
          val errorMessage = GameEvent.eventToJson(Error("Invalid coordinated"))
          secondPlayer.sendMessage(stringMove)
          secondPlayer.expectMessage(errorMessage)
          pushEvent(Move(GameSide.Toe, 1, 0), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 1, 1), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 1, 2), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 2, 2), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Win(GameSide.Tic), waiter = Seq(firstPlayer, secondPlayer))
          firstPlayer.sendCompletion()
          firstPlayer.expectCompletion()
          secondPlayer.sendCompletion()
          secondPlayer.expectCompletion()
        }
      }
    }
    "send UserLeft message if opponent lost connection" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Tic)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val firstPlayer = WSProbe()
      val secondPlayer = WSProbe()
      WS(s"/game/${game.id}:${GameSide.Tic}", firstPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
        WS(s"/game/${game.id}:${GameSide.Toe}", secondPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
          pushEvent(GameStart, waiter = Seq(firstPlayer, secondPlayer))
          firstPlayer.sendCompletion()
          firstPlayer.expectCompletion()
          secondPlayer.expectMessage(GameEvent.eventToJson(UserLeft(GameSide.Tic)))
        }
      }
    }
    "tic should win" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Tic)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val firstPlayer = WSProbe()
      val secondPlayer = WSProbe()
      WS(s"/game/${game.id}:${GameSide.Tic}", firstPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
        WS(s"/game/${game.id}:${GameSide.Toe}", secondPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
          pushEvent(GameStart, waiter = Seq(firstPlayer, secondPlayer))
          pushEvent(Move(GameSide.Tic, 0, 0), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 1, 0), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 1, 1), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 1, 2), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 2, 2), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Win(GameSide.Tic), waiter = Seq(firstPlayer, secondPlayer))
          firstPlayer.sendCompletion()
          firstPlayer.expectCompletion()
          secondPlayer.sendCompletion()
          secondPlayer.expectCompletion()
        }
      }
    }
    "draw should happened" in {
      val game = gameServer.underlyingActor.games.createNewGame(GameSide.Tic)(gameServer.underlyingActor.context, gameServer.underlyingActor.dbActor)
      val firstUrl = game.connectionUrl(host, port, isFirst = true)
      val firstPlayer = WSProbe(maxChunkCollectionMills = 10000)
      val secondPlayer = WSProbe(maxChunkCollectionMills = 10000)
      WS(s"/game/${game.id}:${GameSide.Tic}", firstPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
        isWebSocketUpgrade shouldEqual true
        WS(s"/game/${game.id}:${GameSide.Toe}", secondPlayer.flow) ~> gameServer.underlyingActor.wsRoute ~> check {
          pushEvent(GameStart, waiter = Seq(firstPlayer, secondPlayer))
          pushEvent(Move(GameSide.Tic, 1, 1), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 0, 0), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 0, 1), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 2, 0), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 0, 2), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 1, 2), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 2, 2), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Move(GameSide.Toe, 2, 1), Seq(secondPlayer), Seq(firstPlayer))
          pushEvent(Move(GameSide.Tic, 1, 0), Seq(firstPlayer), Seq(secondPlayer))
          pushEvent(Draw, waiter = Seq(firstPlayer, secondPlayer))
          firstPlayer.sendCompletion()
          firstPlayer.expectCompletion()
          secondPlayer.sendCompletion()
          secondPlayer.expectCompletion()
        }
      }
    }
    "should be parallel" in {
      val count = new AtomicInteger()
      val gamesSize = 10
      for {
        i <- 0 until gamesSize
      } Future {
        runDefaultGame()
      } (scala.concurrent.ExecutionContext.global) andThen {
        case _ => count.incrementAndGet()
      }
      while (count.get() != gamesSize) {
        Thread.sleep(100)
      }
    }
  }

  def runDefaultGame(): Unit = {
    val firstPlayer = WSProbe()
    val secondPlayer = WSProbe()
    Post("/createGame", CreateGameRequest(GameSide.Tic)) ~> route ~> check {
      val createGameResponse = responseAs[CreateGameResponse]
      createGameResponse.status shouldEqual "OK"
      createGameResponse.url.isDefined shouldEqual true
      createGameResponse.gameId.isDefined shouldEqual true
      val gameId = createGameResponse.gameId.get
      Post("/joinGame", JoinGameRequest(gameId)) ~> route ~> check {
        val joinGameResponse = responseAs[JoinGameResponse]
        if (joinGameResponse.status == "Fail") println(joinGameResponse.errorMessage + s" gameId : $gameId")
        joinGameResponse.status shouldEqual "OK"
        joinGameResponse.url.isDefined shouldEqual true
        WS(s"/game/$gameId:${GameSide.Tic}", firstPlayer.flow) ~> wsRoute ~> check {
          isWebSocketUpgrade shouldEqual true
          WS(s"/game/$gameId:${GameSide.Toe}", secondPlayer.flow) ~> wsRoute ~> check {
            pushEvent(GameStart, waiter = Seq(firstPlayer, secondPlayer))
            pushEvent(Move(GameSide.Tic, 1, 1), Seq(firstPlayer), Seq(secondPlayer))
            pushEvent(Move(GameSide.Toe, 0, 0), Seq(secondPlayer), Seq(firstPlayer))
            pushEvent(Move(GameSide.Tic, 0, 1), Seq(firstPlayer), Seq(secondPlayer))
            pushEvent(Move(GameSide.Toe, 2, 0), Seq(secondPlayer), Seq(firstPlayer))
            pushEvent(Move(GameSide.Tic, 0, 2), Seq(firstPlayer), Seq(secondPlayer))
            pushEvent(Move(GameSide.Toe, 1, 2), Seq(secondPlayer), Seq(firstPlayer))
            pushEvent(Move(GameSide.Tic, 2, 2), Seq(firstPlayer), Seq(secondPlayer))
            pushEvent(Move(GameSide.Toe, 2, 1), Seq(secondPlayer), Seq(firstPlayer))
            pushEvent(Move(GameSide.Tic, 1, 0), Seq(firstPlayer), Seq(secondPlayer))
            pushEvent(Draw, waiter = Seq(firstPlayer, secondPlayer))
            firstPlayer.sendCompletion()
            firstPlayer.expectCompletion()
            secondPlayer.sendCompletion()
            secondPlayer.expectCompletion()
          }
        }
      }
    }
  }

  def pushEvent(gameEvent: GameEvent, sender: Seq[WSProbe] = Seq.empty, waiter: Seq[WSProbe] = Seq.empty): Unit = {
    val stringMove = GameEvent.eventToJson(gameEvent)
    sender.foreach(_.sendMessage(stringMove))
    waiter.foreach(_.expectMessage(stringMove))
  }
}
