package finalproject.com

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import finalproject.com.EventRegistry._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.util.Timeout
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.ws.rs
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, GET, POST, Produces}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, DurationInt}

//#import-json-formats
//#user-routes-class
@rs.Path("/events")
class EventRoutes(eventRegistry: ActorRef[EventRegistry.Command])(implicit val system: ActorSystem[_]) {

  //#user-routes-class
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  //#import-json-formats

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.durationToTimeout(Duration(20, TimeUnit.SECONDS))

  @GET
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Returns NASA events", description = "Return NASA request",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "NASA response",
        content = Array(new Content(schema = new Schema(implementation = classOf[NasaEvent])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def getEvents(): Future[NasaEvent] =
    eventRegistry.ask(GetEvents)

  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Creates Google Calendar events", description = "Creates Google Calendar events",
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
        pathEnd {
          concat(
            get {
              complete(getEvents())
            })
            post{
              entity(as[GoogleEvent]){
                x => onSuccess(createEvent(x)){ performed =>
                  complete((StatusCodes.Created, performed))
                }
              }
            }
        })
    }
}
