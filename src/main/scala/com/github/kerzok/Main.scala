package com.github.kerzok

import akka.actor.{ActorSystem, PoisonPill, Props}
import com.github.kerzok.Model.StopListening

import scala.io.StdIn

/**
  * Created by kerzo on 08.10.2016.
  */
object Main extends App {
  val system = ActorSystem("ticTacSystem")
  val gameManager = system.actorOf(Props(classOf[GameManager], "localhost", 8080))
  val s = StdIn.readLine()
  gameManager ! StopListening
}
