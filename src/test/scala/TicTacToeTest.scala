import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import com.github.kerzok.Utils.GameSide
import com.github.kerzok.Utils.GameSide.GameSide
import com.github.kerzok.{DbActor, GameActor, Move}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * Created by kerzo on 12.10.2016.
  */
class TicTacToeTest extends TestKit(ActorSystem("TicTacToe-test-system")) with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  val dbActor = system.actorOf(Props(classOf[DbActor]))
  val gameActor = TestActorRef[GameActor](Props(classOf[GameActor], UUID.randomUUID().toString, dbActor))
  def isWin(move: Move): Boolean = gameActor.underlyingActor.isWin(move)
  def isDraw: Boolean = gameActor.underlyingActor.isDraw
  def opponentSide(side: GameSide) = GameSide.opponentSide(side)
  def setBoard(newBoard: Array[Array[Option[GameSide]]]) = {
    for {
      i <- newBoard.indices
      j <- newBoard.indices
    } gameActor.underlyingActor.board(i)(j) = newBoard(i)(j)
  }
  "Check for win" should {
    "check diagonal win" in {
      val board: Array[Array[Option[GameSide]]] = Array(Array(Some(GameSide.Tic), None, None),
        Array(None, Some(GameSide.Tic), None),
        Array(None, None, Some(GameSide.Tic)))
      val move = Move(GameSide.Tic, 2, 2)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check anti diagonal win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(None, None, Some(GameSide.Tic)),
        Array(None, Some(GameSide.Tic), None),
        Array(Some(GameSide.Tic), None, None))
      val move = Move(GameSide.Tic, 1, 1)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check 1 row win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(Some(GameSide.Tic), Some(GameSide.Tic), Some(GameSide.Tic)),
          Array(None, None, None),
          Array(None, None, None))
      val move = Move(GameSide.Tic, 0, 0)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check 2 row win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(None, None, None),
          Array(Some(GameSide.Tic), Some(GameSide.Tic), Some(GameSide.Tic)),
          Array(None, None, None))
      val move = Move(GameSide.Tic, 1, 1)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check 3 row win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(None, None, None),
          Array(None, None, None),
          Array(Some(GameSide.Tic), Some(GameSide.Tic), Some(GameSide.Tic)))
      val move = Move(GameSide.Tic, 2, 2)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check 1 column win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(Some(GameSide.Tic), None, None),
          Array(Some(GameSide.Tic), None, None),
          Array(Some(GameSide.Tic), None, None))
      val move = Move(GameSide.Tic, 0, 0)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check 2 column win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(None, Some(GameSide.Tic), None),
          Array(None, Some(GameSide.Tic), None),
          Array(None, Some(GameSide.Tic), None))
      val move = Move(GameSide.Tic, 1, 1)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check 3 column win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(None, None, Some(GameSide.Tic)),
          Array(None, None, Some(GameSide.Tic)),
          Array(None, None, Some(GameSide.Tic)))
      val move = Move(GameSide.Tic, 2, 2)
      setBoard(board)
      isWin(move) shouldBe true
    }
    "check draw is not win" in {
      val board: Array[Array[Option[GameSide]]] =
        Array(Array(Some(GameSide.Tic), Some(GameSide.Toe), Some(GameSide.Tic)),
          Array(Some(GameSide.Toe), Some(GameSide.Tic), Some(GameSide.Toe)),
          Array(Some(GameSide.Toe), Some(GameSide.Tic), Some(GameSide.Toe)))
      val move = Move(GameSide.Tic, 0, 0)
      setBoard(board)
      isWin(move) shouldBe false
    }
  }
}