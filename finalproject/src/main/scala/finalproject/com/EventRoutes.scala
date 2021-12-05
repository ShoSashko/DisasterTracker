package finalproject.com

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import finalproject.com.EventRegistry._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, DurationInt}

//#import-json-formats
//#user-routes-class
class EventRoutes(eventRegistry: ActorRef[EventRegistry.Command])(implicit val system: ActorSystem[_]) {

  //#user-routes-class
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  //#import-json-formats

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.durationToTimeout(Duration(20, TimeUnit.SECONDS))

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
