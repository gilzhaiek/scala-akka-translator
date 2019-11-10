name := "scala-akka-translator"

version := "0.1"

scalaVersion := "2.12.6"

lazy val akkaVersion = "2.6.0-M4"
lazy val playVersion = "2.8.0-M3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.9"

)
