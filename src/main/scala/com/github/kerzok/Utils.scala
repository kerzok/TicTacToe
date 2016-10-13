package com.github.kerzok

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.kerzok.Model._
import com.github.kerzok.Utils.GameSide.GameSide
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

/**
  * Created by kerzo on 09.10.2016.
  */
object Utils {
  object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    import GameSideJsonFormat._
    implicit val createGameRequest = jsonFormat1(CreateGameRequest)
    implicit val joinGameRequestFormat = jsonFormat1(JoinGameRequest)
    implicit val joinGameResponseFormat = jsonFormat4(JoinGameResponse)
    implicit val createGameResponseFormat = jsonFormat4(CreateGameResponse)
  }

  object GameSide extends Enumeration {
    type GameSide = Value
    val Tic, Toe = Value

    def opponentSide(side: GameSide) = if (side == GameSide.Tic) GameSide.Toe else GameSide.Tic
  }

  implicit object GameSideJsonFormat extends RootJsonFormat[GameSide] {
    override def read(json: JsValue): GameSide = json match {
      case JsString(value) => GameSide.withName(value)
      case _ => throw DeserializationException("GameSide values expected")
    }

    override def write(obj: GameSide): JsValue = JsString(obj.toString)
  }
}
