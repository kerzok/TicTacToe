package com.github.kerzok

import com.github.kerzok.Utils.GameSide.GameSide

/**
  * Created by kerzo on 09.10.2016.
  */
object Model {
  case class GameFinished(gameId: String)

  case object StopListening

  final case class CreateGameRequest(side: GameSide)
  final case class CreateGameResponse(status: String, gameId: Option[String] = None, url: Option[String] = None, errorMessage: Option[String] = None)

  final case class JoinGameRequest(gameId: String)

  final case class JoinGameResponse(status: String, url: Option[String] = None, side: Option[GameSide] = None, errorMessage: Option[String] = None)

  case class ConnectionException(message: String) extends Exception
}
