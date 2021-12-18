package finalproject.com

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import finalproject.com.EventRegistry._
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.ws.rs
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, GET, POST, Produces}

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.Duration

//#import-json-formats
//#user-routes-class
@rs.Path("/events")
class EventRoutes(eventRegistry: ActorRef[EventRegistry.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.durationToTimeout(Duration(2000, TimeUnit.SECONDS))

  @rs.Path("/google")
  @GET
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Returns Google events", description = "Return Google request",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "NASA response",
        content = Array(new Content(schema = new Schema(implementation = classOf[List[GoogleEvent]])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def getGoogleEvents(start : String, end: String): Future[List[GoogleEvent]] =
    eventRegistry.ask(GetFutureGoogleEvents(start,end, _))

  @rs.Path("/nasa")
  @GET
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Returns NASA events", description = "Return NASA request",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "NASA response",
        content = Array(new Content(schema = new Schema(implementation = classOf[NasaEvent])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def getEvents(): Future[NasaEvent] =
    eventRegistry.ask(GetNasaEvents)

  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Creates Google Calendar events", description = "Creates Google Calendar events",
    requestBody = new RequestBody(content = Array(new Content(
      schema = new Schema(implementation = classOf[GoogleEvent]),
      examples = Array(new ExampleObject(value = """{
  "start": "2021-12-12T09:00:00-09:01",
  "end": "2021-12-12T09:05:00-09:10",
  "location": "Lviv, Ukraine",
  "description": "Event in Lviv",
  "bbox": "-129.02,50.73,-58.71,12.89",
  "eventId": ""
                                                   }"""))
    ))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Google response",
        content = Array(new Content(schema = new Schema(implementation = classOf[ActionPerformed])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def createEvent(event: GoogleEvent): Future[ActionPerformed] =
    eventRegistry.ask(CreateEvent(event, _))

  //#all-routes
  val eventsRoutes: Route =
    pathPrefix("events") {
      concat(
            pathSuffix("nasa"){
              get {
                complete(getEvents())
              }
            },
            pathSuffix("google"){
              get {
                parameters('start.as[String], 'end.as[String]) { (start, end) =>
                  complete(getGoogleEvents(start, end))
                }
              }
            },
            post {
              entity(as[GoogleEvent]) {
                x =>
                  onSuccess(createEvent(x)) { performed =>
                    complete((StatusCodes.Created, performed))
                  }
              }
            }
        )
    }
}
