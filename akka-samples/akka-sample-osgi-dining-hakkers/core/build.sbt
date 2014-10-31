libraryDependencies ++= Dependencies.core

OsgiKeys.bundleActivator := Option("akka.sample.osgi.activation.Activator")
OsgiKeys.privatePackage := Seq("akka.sample.osgi.internal", "akka.sample.osgi.activation", "akka.sample.osgi.service")
