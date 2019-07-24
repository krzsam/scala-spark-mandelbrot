name := "scala-spark-mandelbrot"

version := "0.3"

scalaVersion := "2.11.12"

// for sbt-assembly
resolvers += Resolver.url("https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/").
  withPatterns( Patterns( "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/com.eed3si9n/[module]/scala_2.12/sbt_1.0/[revision]/ivys/ivy.xml" ) )

libraryDependencies += "org.slf4j"          % "slf4j-api"     % "1.7.25"
libraryDependencies += "org.slf4j"          % "slf4j-log4j12" % "1.7.26"
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies += "org.apache.spark"   %% "spark-sql"     % "2.4.3" % "provided"


