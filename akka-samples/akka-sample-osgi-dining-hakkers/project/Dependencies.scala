import sbt._

object Dependencies {
  object Versions {
    val scalaVersion = "2.10.4"
    val akkaVersion = "2.4-SNAPSHOT"
  }

  val akkaActor       = "com.typesafe.akka"            %% "akka-actor"                              % Versions.akkaVersion
  val akkaCluster     = "com.typesafe.akka"            %% "akka-cluster"                            % Versions.akkaVersion
  val akkaPersistence = "com.typesafe.akka"            %% "akka-persistence-experimental"           % Versions.akkaVersion
  val akkaOsgi        = "com.typesafe.akka"            %% "akka-osgi"                               % Versions.akkaVersion

  val osgiCore        = "org.osgi"                      % "org.osgi.core"                           % "4.3.1"                         // ApacheV2
  val osgiCompendium  = "org.osgi"                      % "org.osgi.compendium"                     % "4.3.1"                         // ApacheV2

  object Test {
    val akkaTestkit   = "com.typesafe.akka"            %% "akka-testkit"                            % Versions.akkaVersion  % "test"
    val karafExam     = "org.apache.karaf.tooling.exam" % "org.apache.karaf.tooling.exam.container" % "2.3.1"               % "test"  // ApacheV2
    val paxExam       = "org.ops4j.pax.exam"            % "pax-exam-junit4"                         % "2.6.0"               % "test"  // ApacheV2
    val junit         = "junit"                         % "junit"                                   % "4.10"                % "test"  // Common Public License 1.0
    val scalatest     = "org.scalatest"                %% "scalatest"                               % "2.1.3"               % "test"  // ApacheV2
  }

  val api = Seq(akkaActor)
  val command = Seq(akkaActor, osgiCore, osgiCompendium)
  val core = Seq(akkaCluster, akkaPersistence, akkaOsgi, osgiCore, osgiCompendium)
  val test = Seq(osgiCore, osgiCompendium, Test.akkaTestkit, Test.karafExam, Test.paxExam, Test.junit, Test.scalatest)
}