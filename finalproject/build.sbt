lazy val akkaHttpVersion = "10.2.7"
lazy val akkaVersion    = "2.6.17"
lazy val swaggerVersion = "2.1.11"
lazy val jacksonVersion = "2.13.0"

val swaggerDependencies = Seq(
  "jakarta.ws.rs" % "jakarta.ws.rs-api" % "3.0.0",
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.6.0",
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.5.2",
  "com.github.swagger-akka-http" %% "swagger-enumeratum-module" % "2.3.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "io.swagger.core.v3" % "swagger-jaxrs2-jakarta" % swaggerVersion
)

/**
 * Leave out swaggerUIDependencies if you don't want to include the swaggerUI.
 * See also SwaggerDocService
 */
val swaggerUIDependencies = Seq(
  "org.webjars" % "webjars-locator" % "0.42",
  "org.webjars" % "swagger-ui" % "3.52.5",
)

val googleCalendarDependencies = Seq("com.google.api-client" % "google-api-client" % "1.23.0"
, "com.google.oauth-client" % "google-oauth-client-jetty" % "1.23.0"
, "com.google.apis" % "google-api-services-calendar" % "v3-rev20211026-1.32.1")
resolvers += "google-api-services" at "https://google-api-client-libraries.appspot.com/mavenrepo"


lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "finalproject.com",
      scalaVersion    := "2.13.4"
    )),
    name := "FinalProject",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "ch.qos.logback"    % "logback-classic"           % "1.2.3",
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.1.4"         % Test

    ) ++ swaggerDependencies ++ swaggerUIDependencies ++ googleCalendarDependencies
  )

