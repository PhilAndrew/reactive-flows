lazy val reactiveFlows = project
  .in(file("."))
  .enablePlugins(JavaServerAppPackaging)

name := "reactive-flows"

libraryDependencies ++= List(
  Library.akkaActor,
  Library.akkaCluster,
  Library.akkaContrib,
  Library.akkaDataReplication,
  Library.akkaHttp,
  Library.akkaPersistenceMongo,
  Library.akkaSlf4j,
  Library.akkaSse,
  Library.logbackClassic,
  Library.sprayJson,
  Library.akkaHttpTestkit % "test",
  Library.akkaTestkit     % "test",
  Library.scalaMock       % "test",
  Library.scalaTest       % "test"
)

resolvers ++= List(
  Resolver.hseeberger,
  Resolver.patriknw
)

initialCommands := """|import de.heikoseeberger.reactiveflows._""".stripMargin

addCommandAlias("rf1", "reStart -Dakka.remote.netty.tcp.port=2551 -Dakka.cluster.roles.0=reactive-flows -Dreactive-flows.http-service.port=9001")
addCommandAlias("rf2", "run     -Dakka.remote.netty.tcp.port=2552 -Dakka.cluster.roles.0=reactive-flows -Dreactive-flows.http-service.port=9002")
addCommandAlias("rf3", "run     -Dakka.remote.netty.tcp.port=2553 -Dakka.cluster.roles.0=reactive-flows -Dreactive-flows.http-service.port=9003")

maintainer in Docker := "mail@heikoseeberger.de"
version in Docker := "latest"
dockerBaseImage := "dockerfile/java:oracle-java8"
dockerExposedPorts := Seq(2552, 9000)
dockerRepository := Some("hseeberger")
