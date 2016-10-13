package com.github.kerzok

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.stream.{ConnectionException => _, _}
import ch.megard.akka.http.cors.CorsDirectives._
import com.github.kerzok.Model._
import com.github.kerzok.Utils.GameSide
import com.github.kerzok.Utils.GameSide.GameSide
import com.github.kerzok.Utils.JsonSupport._

import scala.collection._
import scala.collection.convert.decorateAsScala._
import scala.concurrent.Future

/**
  * Created by kerzo on 07.10.2016.
  */
class GameManager(host: String, port: Int) extends PersistentActor with ActorLogging {
  var games = GameStore()
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.system.dispatcher
  implicit val dbActor = context.actorOf(Props(classOf[DbActor]))
  val monitor = context.watch(dbActor)
  val route: Route = cors() {
    path("createGame") {
      post {
        decodeRequest {
          entity(as[CreateGameRequest]) { createGameRequest =>
            val game = games.createNewGame(createGameRequest.side)
            log.info(s"Create new game with id ${game.id}")
            complete(CreateGameResponse("OK", Some(game.id), Some(game.connectionUrl(host, port + 1, isFirst = true))))
          }
        }
      }
    } ~ path("joinGame") {
      post {
        decodeRequest {
          entity(as[JoinGameRequest]) { gameRequest =>
            log.info(s"Request for join to game with id ${gameRequest.gameId}")
            games.get(gameRequest) match {
              case Some(game) =>
                complete(JoinGameResponse("OK", Some(game.connectionUrl(host, port + 1, isFirst = false)), Some(game.getSide(false))))
              case None =>
                complete(JoinGameResponse("Fail", errorMessage = Some("There is no game with such id")))
            }
          }
        }
      }
    }
  }

  val wsRoute = pathPrefix("game" / Remaining) {args => pathEnd {
      val Array(gameId, stringSide) = args.split(":")
      val side = GameSide.withName(stringSide)
      try {
        handleWebSocketMessages(games.get(gameId).websocketFlow(side))
      } catch {
        case ex: ConnectionException => complete(JoinGameResponse("Fail", Some("There is some problem")))
        case ex: Throwable => complete(JoinGameResponse("Fail", Some(ex.getMessage)))
      }
    }
  }

  private[this] val binderOption: Future[ServerBinding] = Http().bindAndHandle(route, host, port)
  private[this] val wsBinderOption: Future[ServerBinding] = Http().bindAndHandle(wsRoute, host, port + 1)
  log.info(s"Server is now online at http://$host:$port")
  log.info(s"Websocket server is now online at ws://$host:${port + 1}")

  override def receiveCommand: Receive = {
    case GameFinished(gameId) =>
      log.info(s"Game with id $gameId finished!")
      games.finish(gameId)
    case StopListening =>
      binderOption.flatMap(_.unbind()).onSuccess({
        case _ => context.system.terminate()
      })
      wsBinderOption.flatMap(_.unbind()).onSuccess({
        case _ => context.system.terminate()
      })
    case SaveSnapshot =>
      val gameIds = games.getGameIds
      saveSnapshot(gameIds)
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, gameIds: Seq[String]) => gameIds.foreach(gameId => games.recreateGame(gameId))
  }

  override def persistenceId: String = "GameManager"
}

case class GameStore()(implicit val self: ActorRef) {
  var games: concurrent.Map[String, Game] = new ConcurrentHashMap[String, Game]().asScala
  def createNewGame(gameSide: GameSide)(implicit actorContext: ActorContext, dbActorRef: ActorRef): Game = {
    val newGameId = gameId
    val game = new Game(newGameId, gameSide, actorContext, dbActorRef)
    games += newGameId -> game
    self.tell(SaveSnapshot, self)
    game
  }

  def recreateGame(gameId: String)(implicit actorContext: ActorContext, dbActorRef: ActorRef): Game = {
    val game = new Game(gameId, GameSide.Tic, actorContext, dbActorRef)
    games += gameId -> game
    game
  }

  def get(id: String): Game = {
    games(id)
  }

  def get(joinGameRequestOpt: Option[JoinGameRequest]): Option[Game] = {
    joinGameRequestOpt.flatMap(request => games.get(request.gameId))
  }

  def get(joinGameRequest: JoinGameRequest): Option[Game] = {
    games.get(joinGameRequest.gameId)
  }

  def finish(gameId: String): Unit = {
    self.tell(SaveSnapshot, self)
    games -= gameId
  }

  def getGameIds: Seq[String] = games.keys.toSeq

  private def gameId: String = UUID.randomUUID().toString
}
