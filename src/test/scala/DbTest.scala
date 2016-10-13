import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.github.kerzok.Utils.GameSide
import com.github.kerzok._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * Created by kerzo on 13.10.2016.
  */
class DbTest extends TestKit(ActorSystem("db-test-system"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  val dbActor = system.actorOf(Props(classOf[DbActor]))
  "DbActor " should {
    "correct save game events" in {
      val gameId = UUID.randomUUID().toString
      val game = system.actorOf(Props(classOf[GameActor], gameId, dbActor))
      game ! UserJoined(GameSide.Tic, self)
      game ! UserJoined(GameSide.Toe, self)
      expectMsg(GameStart)
      expectMsg(GameStart)
      game ! Move(GameSide.Tic, 0, 0)
      expectMsg(Move(GameSide.Tic, 0, 0))
      game ! Move(GameSide.Toe, 1, 0)
      expectMsg(Move(GameSide.Toe, 1, 0))
      game ! Move(GameSide.Tic, 1, 1)
      expectMsg(Move(GameSide.Tic, 1, 1))
      game ! Move(GameSide.Toe, 2, 0)
      expectMsg(Move(GameSide.Toe, 2, 0))
      game ! Move(GameSide.Tic, 2, 2)
      expectMsg(Move(GameSide.Tic, 2, 2))
      expectMsg(Win(GameSide.Tic))
      expectMsg(Win(GameSide.Tic))
      dbActor ! GetGame(gameId)
      expectMsg(Some(StoredEvents(gameId,
        Seq(GameStart,
          Move(GameSide.Tic, 0, 0),
          Move(GameSide.Toe, 1, 0),
          Move(GameSide.Tic, 1, 1),
          Move(GameSide.Toe, 2, 0),
          Move(GameSide.Tic, 2, 2),
          Win(GameSide.Tic)
        ))))
    }
    "resnose None for not existing gameId" in {
      dbActor ! GetGame(UUID.randomUUID().toString)
      expectMsg(None)
    }
  }
}
