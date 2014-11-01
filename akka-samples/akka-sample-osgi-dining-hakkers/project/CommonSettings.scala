import com.typesafe.sbt.osgi.SbtOsgi._
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object CommonSettings extends AutoPlugin {

  def defaultImports = Seq("!sun.misc", akkaImport(), configImport(), scalaImport(), "*")
  def akkaImport(packageName: String = "akka.*") = versionedImport(packageName, "2.4", "2.5")
  def configImport(packageName: String = "com.typesafe.config.*") = versionedImport(packageName, "1.2.0", "1.3.0")
  def scalaImport(packageName: String = "scala.*") = versionedImport(packageName, s"$scalaEpoch.$scalaMajor", s"$scalaEpoch.${scalaMajor+1}")
  def versionedImport(packageName: String, lower: String, upper: String) = s"""$packageName;version="[$lower,$upper)""""
  val Seq(scalaEpoch, scalaMajor) = """(\d+)\.(\d+)\..*""".r.unapplySeq(Dependencies.Versions.scalaVersion).get.map(_.toInt)

  override def requires: Plugins = JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    organization := "com.typesafe.akka",
    version := "2.4-SNAPSHOT",

    target := baseDirectory.value / "target-sbt",

    // The included osgiSettings that creates bundles also publish the jar files
    // in the .../bundles directory which makes testing locally published artifacts
    // a pain. Create bundles but publish them to the normal .../jars directory.
    packagedArtifact in (Compile, packageBin) <<= (artifact in (Compile, packageBin), OsgiKeys.bundle).identityMap,

    OsgiKeys.importPackage := defaultImports
  ) ++ defaultOsgiSettings

}
