/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka

import sbt._
import sbt.Keys._

object TimeStampede extends AutoPlugin {

  override def requires = RootSettings
  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    commands ++= Seq(
      Command.command("stampVersion")(stampVersion),

      /**
       * Used for cross-publishing stamped versions. Needed because time stamped
       * versions are reset on scala version change if used with +publish.
       */
      Command.command("stampVersionAndPublish")(stampVersion andThen publish)
    )
  )

  final val Snapshot = "-SNAPSHOT"

  private val stampVersion: State => State = (state) => {
    val extracted = Project.extract(state)
    extracted.append(List(version in ThisBuild ~= stamp), state)
  }

  private val publish: State => State = (state) => {
    val extracted = Project.extract(state)
    val projectRef = extracted.get(thisProjectRef)
    extracted.runAggregated(Keys.publish in projectRef, state)
  }

  private def stamp(version: String): String = {
    if (version endsWith Snapshot) (version stripSuffix Snapshot) + "-" + timestamp(System.currentTimeMillis)
    else version
  }

  private def timestamp(time: Long): String = {
    val format = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
    format.format(new java.util.Date(time))
  }
}
