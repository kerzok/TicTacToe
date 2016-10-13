package com.github.kerzok

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorContext, ActorRef, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.stage._
import com.github.kerzok.Model.ConnectionException
import com.github.kerzok.Utils.GameSide
import com.github.kerzok.Utils.GameSide.GameSide
import org.json4s._
import org.json4s.native.JsonMethods._

/**
  * Created by kerzo on 07.10.2016.
  */
class Game(val id: String, firstUserSide: GameSide, context: ActorContext, dbActorRef: ActorRef) {
  private[this] val gameActor = context.actorOf(Props(classOf[GameActor], id, dbActorRef), s"game-$id")
  private[this] val playersCount = new AtomicInteger(0)

  def connectionUrl(host: String, port: Int, isFirst: Boolean): String = {
    val gameSide = if (isFirst) firstUserSide else GameSide.opponentSide(firstUserSide)
    "ws://" + s"$host:$port" + "/game/" + id + ":" + gameSide.toString
  }

  def getSide(isFirst: Boolean): GameSide = if (isFirst) firstUserSide else GameSide.opponentSide(firstUserSide)

  def websocketFlow(side: GameSide): Flow[Message, Message, _] = {
    if (playersCount.incrementAndGet() <= 2) {
      Flow.fromGraph(GraphDSL.create(Source.actorRef[GameEvent](5, OverflowStrategy.fail)) {
        implicit builder ⇒ playerActor ⇒
          import GraphDSL.Implicits._
          val materialization = builder.materializedValue.map(playerActorRef => UserJoined(side, playerActorRef))
          val merge = builder.add(Merge[GameEvent](2))

          val messageToGameEventFlow = builder.add(Flow[Message].collect {
            case TextMessage.Strict(text) => GameEvent.parseEvent(parse(text))
          })
          val gameEventToMessageFlow = builder.add(Flow[GameEvent].collect {
            case event: GameEvent => TextMessage.Strict(GameEvent.eventToJson(event))
          })
          val sink = Sink.actorRef[GameEvent](gameActor, UserLeft(side))
          materialization ~> merge ~> sink
          messageToGameEventFlow ~> merge
          playerActor ~> gameEventToMessageFlow
          FlowShape(messageToGameEventFlow.in, gameEventToMessageFlow.out)
      }).via(closeStage)
    } else {
      throw ConnectionException("You cannot connect to this game")
    }
  }

  val closeStage = new GraphStage[FlowShape[Message, Message]] {
    val in = Inlet[Message]("closeStage.in")
    val out = Outlet[Message]("closeStage.out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush() = grab(in) match {
          case msg@TextMessage.Strict(txt) if txt.contains("Win") || txt.contains("Draw") ⇒
            push(out, msg)
            completeStage()
          case msg ⇒ push(out, msg)
        }
      })
      setHandler(out, new OutHandler {
        override def onPull() = pull(in)
      })
    }
  }
}
