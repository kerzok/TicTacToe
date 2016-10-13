package com.github.kerzok

import akka.actor.ActorRef
import com.github.kerzok.Utils.GameSide
import com.github.kerzok.Utils.GameSide.GameSide
import org.json4s._
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization.write
import org.json4s.native.JsonMethods._
/**
  * Created by kerzo on 09.10.2016.
  */
sealed trait GameEvent

case class UserJoined(side: GameSide, userActor: ActorRef) extends GameEvent
case class UserLeft(side: GameSide) extends GameEvent
case class Move(side: GameSide, x: Int, y: Int) extends GameEvent
case class Win(side: GameSide) extends GameEvent
case class Error(description: String) extends GameEvent
case object GameStart extends GameEvent
case object Draw extends GameEvent

case object GameEvent {
  implicit lazy val formats = DefaultFormats + GameEventSerializer + new EnumNameSerializer(GameSide)

  object GameEventSerializer extends CustomSerializer[GameEvent](format => ({
      case json: JValue => GameEvent.parseEvent(json)
    }, {
      case UserJoined(side, _) => JObject(JField("eventType", JString("UserJoined")), JField("side", JString(side.toString)))
      case UserLeft(side) => JObject(JField("eventType", JString("UserLeft")), JField("side", JString(side.toString)))
      case Move(side, x, y) => JObject(JField("eventType", JString("Move")), JField("side", JString(side.toString)), JField("x", JInt(x)), JField("y", JInt(y)))
      case Win(side) => JObject(JField("eventType", JString("Win")), JField("side", JString(side.toString)))
      case Error(desc) => JObject(JField("eventType", JString("Error")), JField("description", JString(desc)))
      case GameStart => JObject(JField("eventType", JString("GameStart")))
      case Draw => JObject(JField("eventType", JString("Draw")))
    }))

  def parseEvent(json: JValue): GameEvent = {
    val jsonType = json \\ "eventType"
    jsonType match {
      case jValue@JString("Move") => json.removeField(_ == JField("eventType", jValue)).extract[Move]
      case jValue@JString("UserJoined") => json.removeField(_ == JField("eventType", jValue)).extract[UserJoined]
      case jValue@JString("UserLeft") => json.removeField(_ == JField("eventType", jValue)).extract[UserLeft]
      case jValue@JString("Win") => json.removeField(_ == JField("eventType", jValue)).extract[Win]
      case jValue@JString("Error") => json.removeField(_ == JField("eventType", jValue)).extract[Error]
      case jValue@JString("GameStart") => GameStart
      case jValue@JString("Draw") => Draw
      case _ => throw new IllegalArgumentException("Other types not allowed here")
    }
  }
  def eventToJson(gameEvent: GameEvent): String = write(gameEvent)
}

