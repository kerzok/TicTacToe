package com.github.kerzok

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.PersistentActor
import com.github.kerzok.Model.GameFinished
import com.github.kerzok.Utils.GameSide
import com.github.kerzok.Utils.GameSide._

import scala.annotation.tailrec
/**
  * Created by kerzo on 09.10.2016.
  */
final class GameActor(id: String, dbActor: ActorRef) extends PersistentActor with ActorLogging {
  var players = Map.empty[GameSide, ActorRef]
  val boardSize: Int = 3
  val board = Array.ofDim[Option[GameSide]](boardSize, boardSize)
  for {
     i <- 0 until boardSize
     j <- 0 until boardSize
  } board(i)(j) = None

  var movesCount = 0
  var currentSide = GameSide.Tic
  override def persistenceId: String = s"game-$id"

  override def receiveRecover: Receive = {
    case move: Move => makeMove(move)(isRecover = true)
  }

  override def receiveCommand: Receive = {
    case UserJoined(side, actorRef) if players.size == 2 =>
      actorRef ! Error("Game has already started")
      context.stop(actorRef)
    case UserJoined(side, actorRef) =>
      log.info(s"add $side")
      context.watch(actorRef)
      players += side -> actorRef
      if (players.size == 2) sendEventToAll(GameStart)
    case UserLeft(side) =>
      log.info(s"left $side")
      val ref = players(side)
      context.unwatch(ref)
      context.stop(ref)
      players -= side
      sendEventToSide(opponentSide(side), Error("Your opponent has left"))
    case move@Move(side, _, _) if side == currentSide => persist(move)(makeMove)
    case Move(side, _, _) =>
      val response = Error("Not your turn")
      sendEventToSide(side, response)
  }

  def makeMove(move: Move)(implicit isRecover: Boolean = false) = move match {
    case Move(side, x, y) if checkBorders(x, y) && isPositionEmpty(x, y) =>
      board(x)(y) = Some(side)
      movesCount += 1
      if (isWin(move)) {
        finishGame(move, Win(side))
      } else if (isDraw) {
        finishGame(move, Draw)
      } else {
        currentSide = opponentSide(side)
        sendEventToSide(currentSide, move)
      }
    case Move(side, _, _) =>
      val response = Error("Invalid coordinated")
      sendEventToSide(side, response)
  }

  def checkBorders(x: Int, y: Int): Boolean = x < boardSize && x >= 0 && y < boardSize && y >= 0

  def isPositionEmpty(x: Int, y: Int): Boolean = board(x)(y).isEmpty

  def isDraw = movesCount == boardSize * boardSize

  def saveEventToStorage(gameEvent: GameEvent)(implicit isRecover: Boolean = false): Unit = {
    if (!isRecover) dbActor ! AddGameEvent(id, gameEvent)
  }

  def sendEventToSide(gameSide: GameSide, gameEvent: GameEvent): Unit = {
    saveEventToStorage(gameEvent)
    players.get(gameSide).foreach(_ ! gameEvent)
  }

  def sendEventToAll(gameEvent: GameEvent): Unit = {
    saveEventToStorage(gameEvent)
    players.values.foreach(_ ! gameEvent)
  }

  def isWin(move: Move): Boolean = {
    val Move(side, x, y) = move
    val checkCol = check(x, 0,
      (x, y) => !board(x)(y).contains(side),
      (x, y) => y == boardSize - 1,
      (x, y) => (x, y + 1))
    val checkRow = check(0, y,
      (x, y) => !board(x)(y).contains(side),
      (x, y) => x == boardSize - 1,
      (x, y) => (x + 1, y))
    val checkDiag = check(0, 0,
      (x, y) => !board(x)(y).contains(side),
      (x, y) => x == boardSize - 1,
      (x, y) => (x + 1, y + 1))
    val checkAntiDiag = check(0, boardSize - 1,
      (x, y) => !board(x)(y).contains(side),
      (x, y) => x == boardSize - 1,
      (x, y) => (x + 1, y - 1))
    checkCol || checkRow || checkDiag || checkAntiDiag
  }

  def finishGame(move: Move, result: GameEvent): Unit = {
    sendEventToSide(opponentSide(move.side), move)
    sendEventToAll(result)
    context.parent ! GameFinished(id)
    context.stop(self)
  }

  @tailrec
  def check(x: Int, y: Int,
                          checkFirst: (Int, Int) => Boolean,
                          checkSecond: (Int, Int) => Boolean,
                          moveTo: (Int, Int) => (Int, Int)): Boolean = {
    if (checkFirst(x, y)) false
    else if (checkSecond(x, y)) true
    else {
      val (newX, newY) = moveTo(x, y)
      check(newX, newY, checkFirst, checkSecond, moveTo)
    }
  }

}
