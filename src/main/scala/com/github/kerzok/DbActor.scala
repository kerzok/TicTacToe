package com.github.kerzok

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.mongodb.scala.{MongoClient, _}
import org.mongodb.scala.model.Filters._

import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Created by kerzo on 11.10.2016.
  */
class DbActor extends Actor with ActorLogging {
  import Helpers._
  val client = MongoClient()
  val db = client.getDatabase("tic-tac-toe")

  private val games = db.getCollection("games")

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.info("Stop successful")
    client.close()
  }
  override def receive: Receive = {
    case AddGameEvent(gameId, gameEvent) =>
      val findResults = games.find(equal("_id", gameId)).results()
      if (findResults.isEmpty) {
        games.insertOne(StoredEvents(gameId, Seq(gameEvent))).results()
      }
      findResults.foreach(document => {
        val game: StoredEvents = document
        games.findOneAndReplace(game, game.copy(events = game.events :+ gameEvent)).results()
      })
    case GetGame(gameId) =>
      val findResults = games.find(equal("_id", gameId)).results()
      if (findResults.isEmpty)
        sender() ! None
      else
        sender() ! Some(findResults.head: StoredEvents)
  }
}

object ImplicitGameEventTransformers {
  implicit def gameEvent2Document(document: Document): GameEvent = {
    GameEvent.parseEvent(parse(document.toJson()))
  }

  implicit def document2GameEvent(gameEvent: GameEvent): Document = {
    Document(GameEvent.eventToJson(gameEvent))
  }
}


object Helpers {
  implicit class DocumentObservable[C](val observable: Observable[Document]) extends ImplicitObservable[Document] {
    override val converter: (Document) => String = (doc) => doc.toJson
  }

  implicit class GenericObservable[C](val observable: Observable[C]) extends ImplicitObservable[C] {
    override val converter: (C) => String = (doc) => doc.toString
  }

  trait ImplicitObservable[C] {
    val observable: Observable[C]
    val converter: (C) => String

    def results(): Seq[C] = Await.result(observable.toFuture(), Duration(10, TimeUnit.SECONDS))
  }

}

case class StoredEvents(id: String, events: Seq[GameEvent])
object StoredEvents {
  import ImplicitGameEventTransformers._
  implicit def document2StoredEvents(document: Document): StoredEvents = {
    val id = document("_id").asString().getValue
    import scala.collection.JavaConversions._
    val events = document("events").asArray().getValues.toList.map(bson => Document(bson.asDocument()): GameEvent)
    StoredEvents(id, events)
  }

  implicit def storedEvents2Document(game: StoredEvents): Document = {
    val StoredEvents(id, events) = game
    Document("_id" -> id, "events" -> events.map(event => event: Document))
  }
}
case class AddGameEvent(gameId: String, gameEvent: GameEvent)
case class GetGame(gameId: String)
