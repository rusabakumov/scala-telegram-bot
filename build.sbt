organization := "com.github.rusabakumov"
name := "scala-telegram-bot"
version := "3.2.0-rc1"
scalaVersion := "2.13.8"

bintrayRepository := "rusabakumov-bintray"
bintrayOmitLicense := true

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ywarn-dead-code",
  "-Xfatal-warnings",
  "-Xlint"
)


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.2.7",
  "com.typesafe.akka" %% "akka-stream" % "2.6.19",
  "de.heikoseeberger" %% "akka-http-argonaut" % "1.39.2",
  "ch.qos.logback" %  "logback-classic" % "1.2.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
)
