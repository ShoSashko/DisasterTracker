package finalproject.com

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.{Calendar, CalendarScopes}
import finalproject.com.QuickstartApp.{APPLICATION_NAME, JSON_FACTORY, getCredentials}
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

import java.io.{File, FileNotFoundException, IOException, InputStreamReader}
import java.time.LocalDateTime
import java.util.Collections
import scala.concurrent.{Await, Future}
import scala.concurrent.Future
import com.google.api.services.calendar.model.EventDateTime
import scala.concurrent.duration.Duration

final case class NasaEvent(title: Option[String], description: Option[String], link: Option[String], events: Array[EventObject])
object NasaEvent extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[NasaEvent] = jsonFormat4(NasaEvent.apply)
}

final case class Category(id: Option[String], title: Option[String])
object Category extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[Category] = jsonFormat2(Category.apply)
}

final case class Geometry(magnitudeValue: Option[Int], magnitudeUnit: Option[String], date: Option[String], `type`: Option[String], coordinates: Array[Double])
object Geometry extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[Geometry] = jsonFormat5(Geometry.apply)
}

final case class ActionPerformed(description: String)
object ActionPerformed extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[ActionPerformed] = jsonFormat1(ActionPerformed.apply)
}

final case class EventObject(id: Option[String],
                             title: Option[String],
                             description: Option[String],
                             link: Option[String],
                             closed : Option[String],
                             categories : Array[Category],
                             geometry: Array[Geometry])
object EventObject extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[EventObject] = jsonFormat7(EventObject.apply)
}

final case class GoogleEvent(start: String, end: String, location: String, description: String)
object GoogleEvent extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[GoogleEvent] = jsonFormat4(GoogleEvent.apply)
}


object EventRegistry{
  private val APPLICATION_NAME = "Google Calendar API Java Quickstart"
  // Global instance of the JSON factory.
  private val JSON_FACTORY = GsonFactory.getDefaultInstance
  private val TOKENS_DIRECTORY_PATH = "tokens"


  import java.util

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

  sealed trait Command
  final case class GetEvents(replyTo: ActorRef[NasaEvent]) extends Command
  final case class CreateEvent(event: GoogleEvent, replyTo: ActorRef[ActionPerformed]) extends Command

  def apply(): Behavior[Command] = registry()

  private def registry(): Behavior[Command] =
    Behaviors.receiveMessage {
      case CreateEvent(event, replyTo) =>{
        replyTo ! ActionPerformed(s"event ${event.description} created.")
        val googleEvent = new Event()
          .setSummary(event.description)
          .setLocation("800 Howard St., San Francisco, CA 94103")
          .setDescription(event.description);


        val startDateTime = new DateTime(event.start)
        val start = new EventDateTime().setDateTime(startDateTime).setTimeZone("America/Los_Angeles")
        googleEvent.setStart(start)

        val endDateTime = new DateTime(event.end)
        val end = new EventDateTime().setDateTime(endDateTime).setTimeZone("America/Los_Angeles")
        googleEvent.setEnd(end)

        val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
        val service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build
        val googleEventtest = service.events.insert("primary", googleEvent).execute
        println("Google Success")
        replyTo ! ActionPerformed(s"Event ${event.description} created.")
        Behaviors.same
      }
      case GetEvents(replyTo) =>{
        implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
        implicit val executionContext = system.executionContext

        val responseFuture : Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=10",
          headers = Seq(HttpHeader.parse("Accept", "application/json").asInstanceOf[ParsingResult.Ok].header)))

       val event : NasaEvent = Await.result(responseFuture
          .map(x=>x.entity)
          .flatMap(y => Unmarshal(y).to[NasaEvent]), Duration.Inf)

        println("Success")
        replyTo ! event
        Behaviors.same
      }
    }
}
