package finalproject.com

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.model.{Event, EventDateTime}
import com.google.api.services.calendar.{Calendar, CalendarScopes}
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

import java.io.{File, FileNotFoundException, IOException, InputStreamReader}
import java.util.Collections
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, Future, duration}
import scala.jdk.CollectionConverters.CollectionHasAsScala


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

final case class GoogleEvent(start: String, end: String, location: Option[String], description: String, bbox : String, eventId: String)
object GoogleEvent extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[GoogleEvent] = jsonFormat6(GoogleEvent.apply)
}

object EventRegistry{
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

  sealed trait Command
  final case class GetNasaEvents(replyTo: ActorRef[NasaEvent]) extends Command
  final case class GetFutureGoogleEvents(start : String, end : String, replyTo: ActorRef[List[GoogleEvent]]) extends Command
  final case class CreateEvent(event: GoogleEvent, replyTo: ActorRef[ActionPerformed]) extends Command

  def apply(): Behavior[Command] = registry()

  private def registry(): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetFutureGoogleEvents(start, end, replyTo) =>{
        val googleEvents = GetGoogleEvents(start,end);
        replyTo ! googleEvents
        Behaviors.same
      }
      case CreateEvent(event : GoogleEvent, replyTo) =>{
        replyTo ! CreateGoogleEvent(event)
        Behaviors.same
      }
      case GetNasaEvents(replyTo) =>{
        val event = GetNasaEvent()
        replyTo ! event
        Behaviors.same
      }
    }

  private def CreateGoogleEvent(event : GoogleEvent) : ActionPerformed ={
    val googleEvent = new Event()
      .setSummary(event.description)
      .setLocation(event.location.get)

    if(event.location != None){
      if(Context.hotestPoints.contains(event.location.get)){
        googleEvent.setSummary(event.description + " This place might be dangerous")
      }
    }

    val startDateTime = new DateTime(event.start)
    val start = new EventDateTime().setDateTime(startDateTime).setTimeZone("Europe/Kiev")
    googleEvent.setStart(start)

    val endDateTime = new DateTime(event.end)
    val end = new EventDateTime().setDateTime(endDateTime).setTimeZone("Europe/Kiev")
    googleEvent.setEnd(end)

    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build
    service.events.insert("primary", googleEvent).execute

    ActionPerformed(s"Event ${event.description} created.")
  }

  private def UpdateGoogleEvent(event : GoogleEvent): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
    implicit val executionContext = system.executionContext
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build
    // List the next 10 events from the primary calendar.
    var oldEvent = service.events().get("primary", event.eventId).execute();
    if(!oldEvent.getSummary.contains("ALERT:")) {
      oldEvent.setSummary(oldEvent.getSummary + " ALERT: this place is dangerous " + new DateTime(System.currentTimeMillis))
      oldEvent.setDescription(oldEvent.getDescription + " ALERT")
      oldEvent.setUpdated(new DateTime(System.currentTimeMillis))
      val newEvent = service.events().update("primary", event.eventId, oldEvent).setSendUpdates("all").execute()
    }
  }

  private def GetGoogleEvents(start : String, end: String): List[GoogleEvent] ={
    implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
    implicit val executionContext = system.executionContext
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build

    val format = new java.text.SimpleDateFormat("yyyy-MM-dd")
    val startDateTime = new DateTime(format.parse(start));
    val endDateTime = new DateTime(format.parse(end));
    val events = service.events.list("primary").setMaxResults(10).setTimeMin(startDateTime).setTimeMax(endDateTime).setOrderBy("startTime").setSingleEvents(true).execute

    val result : List[GoogleEvent] = events.getItems.asScala.map((x : Event) => GoogleEvent(x.getStart.toString, x.getEnd.toString, Option(x.getLocation), x.getSummary, "", x.getId) ).toList
      result
  }

  implicit def GetNasaEvent(): NasaEvent ={
    implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
    implicit val executionContext = system.executionContext
    val uri = Uri("https://eonet.gsfc.nasa.gov/api/v3/events") withQuery ("status", "open") +: ("limit", "10") +: ("bbox", "13,54,46,42")  +: Query.Empty
    val responseFuture : Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = uri,
      headers = Seq(HttpHeader.parse("Accept", "application/json").asInstanceOf[ParsingResult.Ok].header)))

    val event : NasaEvent = Await.result(responseFuture
      .map(x => x.entity)
      .flatMap(y => Unmarshal(y).to[NasaEvent]), Duration.Inf)


    Source.tick(FiniteDuration(0, duration.SECONDS), FiniteDuration(5, duration.SECONDS), 1)
      .map(_ => resolveEvent())
      .statefulMapConcat(() => {
        {
          (sequence) => {
            if(Context.nasaEvents.length != sequence.events.length){
              Context.nasaEvents = sequence.events
              for(nasaEvent <- sequence.events){
                if(nasaEvent.title != None ){
                  val cityName = nasaEvent.title.get.substring(nasaEvent.title.get.lastIndexOf("-") + 2)
                  if(!Context.hotestPoints.exists(x=>x._1 == cityName)){
                    Context.hotestPoints += (cityName -> 1)
                  }
                  else{
                    Context.hotestPoints.update(cityName, Context.hotestPoints(cityName) + 1)
                  }
                }
              }
            }
          }
            val googleEvents = GetGoogleEvents("2021-12-22", "2021-12-23")

            for(elem <- googleEvents){
              for(nasaEvent <- sequence.events) {
                if(elem.location != None && elem.location.get.contains("Україна") && nasaEvent.title != None && nasaEvent.title.get.contains("Ukraine")){
                  UpdateGoogleEvent(elem);
                }
              }
            }
            Context.nasaEvents
        }
      }).run()

    event
  }

  def resolveEvent() : NasaEvent = {
    implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
    implicit val executionContext = system.executionContext
    val uri = Uri("https://eonet.gsfc.nasa.gov/api/v3/events") withQuery ("status", "open") +: ("limit", "10") +: ("bbox", "13,54,46,42")  +: Query.Empty
    val responseFuture : Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = uri,
      headers = Seq(HttpHeader.parse("Accept", "application/json").asInstanceOf[ParsingResult.Ok].header)))

    val event : NasaEvent = Await.result(responseFuture
      .map(x => x.entity)
      .flatMap(y => Unmarshal(y).to[NasaEvent]), Duration.Inf)

    event
  }
}

object Context {
  var hotestPoints = collection.mutable.Map[String,Int]();
  var nasaEvents = Array[EventObject]();
}
