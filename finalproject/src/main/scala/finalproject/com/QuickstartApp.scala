package finalproject.com

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.{Calendar, CalendarScopes}
import finalproject.com.swagger.SwaggerDocService

import java.io.{File, FileNotFoundException, IOException, InputStreamReader}
import java.util.Collections
import scala.util.{Failure, Success}

//#main-class
object QuickstartApp {



  private val APPLICATION_NAME = "Google Calendar API Java Quickstart"
  // Global instance of the JSON factory.
  private val JSON_FACTORY = GsonFactory.getDefaultInstance
  private val TOKENS_DIRECTORY_PATH = "tokens"

  private val SCOPES = Collections.singletonList(CalendarScopes.CALENDAR)
  private val CREDENTIALS_FILE_PATH = "credentials.json"



  @throws[IOException]
  private def getCredentials(HTTP_TRANSPORT: NetHttpTransport) = { // Load client secrets.
    val in = QuickstartApp.getClass.getResourceAsStream(CREDENTIALS_FILE_PATH)
    if (in == null) throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH)
    val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in))
    // Build flow and trigger user authorization request.
    val flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES).setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH))).setAccessType("offline").build
    val receiver = new LocalServerReceiver.Builder().setPort(8080).build
    val credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    //returns an authorized Credential object.
    credential
  }


  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {

    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext
    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes ~ SwaggerDocService.routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  //#start-http-server
  def main(args: Array[String]): Unit = {
    //#server-bootstrapping

    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build
    // List the next 10 events from the primary calendar.
    val now = new DateTime(System.currentTimeMillis)
    val events = service.events.list("primary").setMaxResults(10).setTimeMin(now).setOrderBy("startTime").setSingleEvents(true).execute
    val items = events.getItems
    System.out.println("Upcoming events")
    items.forEach(event => System.out.printf("%s (%s)\n", event.getSummary, event.getStart.getDate))

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val eventRegistryActor = context.spawn(EventRegistry(), "EventRegistryActor")
      context.watch(eventRegistryActor)

      val routes = new EventRoutes(eventRegistryActor)(context.system)
      startHttpServer(routes.eventsRoutes)(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
    //#server-bootstrapping
  }
}
//#main-class
