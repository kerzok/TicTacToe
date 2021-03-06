name := "TicTacToe"

version := "1.0"

scalaVersion := "2.11.8"

fork := true
val akkaVersion = "2.4.11"
resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.11",
  "com.typesafe.akka" % "akka-http-experimental_2.11" % "2.4.11",
  "com.typesafe.akka" % "akka-http-core_2.11" % "2.4.11",
  "com.typesafe.akka" % "akka-stream_2.11" % "2.4.11",
  "com.typesafe.akka" % "akka-testkit_2.11" % "2.4.11",
  "com.typesafe.akka" % "akka-persistence_2.11" % "2.4.11",
  "com.typesafe.akka" %% "akka-http-testkit" % "2.4.11",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.11",
  "org.json4s" % "json4s-native_2.11" % "3.4.1",
  "org.json4s" % "json4s-ext_2.10" % "3.4.1",
  "org.scalatest" % "scalatest_2.11" % "3.0.0",
  "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.1",
  "org.iq80.leveldb" % "leveldb" % "0.9",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "ch.megard" %% "akka-http-cors" % "0.1.7",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.10"
)




