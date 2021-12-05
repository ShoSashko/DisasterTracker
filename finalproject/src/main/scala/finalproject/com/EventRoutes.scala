package finalproject.com

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import finalproject.com.EventRegistry._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.Uri.Path
import akka.util.Timeout
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.ws.rs
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.{GET, Produces}

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
        content = Array(new Content(schema = new Schema(implementation = classOf[Event])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def getEvents(): Future[Event] =
    eventRegistry.ask(GetEvents)

  //#all-routes
  val eventsRoutes: Route =
    pathPrefix("events") {
      concat(
        pathEnd {
          concat(
            get {
              complete(getEvents())
            })
        })
    }
}
