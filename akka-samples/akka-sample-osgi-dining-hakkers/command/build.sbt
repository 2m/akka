libraryDependencies ++= Dependencies.command

OsgiKeys.bundleActivator := Option("akka.sample.osgi.command.Activator")
OsgiKeys.privatePackage := Seq("akka.sample.osgi.command")
