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
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

import scala.concurrent.{Await, Future}
import scala.concurrent.Future
import scala.concurrent.duration.Duration

final case class Event(title: Option[String], description: Option[String], link: Option[String], events: Array[EventObject])
object Event extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[Event] = jsonFormat4(Event.apply)
}

final case class Category(id: Option[String], title: Option[String])
object Category extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[Category] = jsonFormat2(Category.apply)
}

final case class Geometry(magnitudeValue: Option[Int], magnitudeUnit: Option[String], date: Option[String], `type`: Option[String], coordinates: Array[Double])
object Geometry extends DefaultJsonProtocol with SprayJsonSupport with NullOptions {
  implicit val format: RootJsonFormat[Geometry] = jsonFormat5(Geometry.apply)
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

object EventRegistry{
  sealed trait Command
  final case class GetEvents(replyTo: ActorRef[Event]) extends Command

  def apply(): Behavior[Command] = registry()

  private def registry(): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetEvents(replyTo) =>{
        implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
        implicit val executionContext = system.executionContext

        val responseFuture : Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=10",
          headers = Seq(HttpHeader.parse("Accept", "application/json").asInstanceOf[ParsingResult.Ok].header)))

       val event : Event = Await.result(responseFuture
          .map(x=>x.entity)
          .flatMap(y => Unmarshal(y).to[Event]), Duration.Inf)

        println("Success")
        replyTo ! event
        Behaviors.same
      }
    }
}
