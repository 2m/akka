package akka

import sbt._
import sbt.Keys._

object Sample {
  def buildTransforer = (ti: BuildLoader.TransformInfo) => ti.base.name match {
    case s if s.startsWith("akka-sample") =>
      val projects = ti.unit.definitions.projects.map { p =>
        p.settings(
          buildDependencies := {
            val projectDependencies = libraryDependencies.value.collect {
              case module if module.organization == "com.typesafe.akka" => ProjectRef(file("").toURI, module.name)
            }
            println(s"Will add $projectDependencies")
            val dependencies = buildDependencies.value
            val classpathWithProjectDependencies = dependencies.classpath.map {
              case (project, deps) if project.project == p.id =>
                // add project dependency for every akka library dependnecy
                (project, deps ++ projectDependencies.map(ResolvedClasspathDependency(_, None)))
              case (project, deps) => (project, deps)
            }
            BuildDependencies(classpathWithProjectDependencies, dependencies.aggregate)
          }/*,
          libraryDependencies := libraryDependencies.value.map {
            case module if module.organization == organization.value =>
              // exclude self, so it is still possible to know what project dependencies to add
              // also this leaves all transitive dependencies (such as typesafe config library)
              println(s"excluding by ${organization.value}")
              module.excludeAll(ExclusionRule(organization=module.organization))
            case module => module
          }*/
        )
      }

      val newDefinitions = new LoadedDefinitions(
        ti.unit.definitions.base,
        ti.unit.definitions.target,
        ti.unit.definitions.loader,
        ti.unit.definitions.builds,
        projects,
        ti.unit.definitions.buildNames
      )

      new BuildUnit(
        ti.unit.uri,
        ti.unit.localBase,
        newDefinitions,
        ti.unit.plugins
      )
    case _ =>
      println(s"skipping ${ti.base.name}")
      ti.unit
  }

  def project(name: String) =
    ProjectRef(file(s"akka-samples/$name"), name)
}
