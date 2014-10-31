lazy val root = (project in file(".")).
  aggregate(api, command)

lazy val api = project
lazy val command = project.dependsOn(api)
lazy val core = project.dependsOn(api)
lazy val `integration-test` = project.dependsOn(command, core)
