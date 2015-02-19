/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.reactiveflows

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.contrib.pattern.DistributedPubSubExtension
import akka.http.Http
import akka.http.model.StatusCodes
import akka.http.server.Directives
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{ ImplicitFlowMaterializer, Source }
import akka.util.Timeout
import de.heikoseeberger.akkasse.EventStreamMarshalling
import scala.concurrent.{ Future, ExecutionContext }
import spray.json.DefaultJsonProtocol

object HttpService {

  import Directives._
  import EventStreamMarshalling._
  import JsonMarshalling._
  import JsonProtocol._
  import ServerSentEventProtocol._

  private[reactiveflows] case object Stop

  private[reactiveflows] case object CreateMessageEventSource

  private[reactiveflows] case object CreateFlowEventSource

  private object AddFlowRequest extends DefaultJsonProtocol {
    implicit val format = jsonFormat1(apply)
  }
  private case class AddFlowRequest(label: String)

  private object AddMessageRequest extends DefaultJsonProtocol {
    implicit val format = jsonFormat1(apply)
  }
  private case class AddMessageRequest(text: String)

  /** Name for the [[HttpService]] actor. */
  final val Name = "http-service"

  /** Factory for [[HttpService]] `Props`. */
  def props(interface: String, port: Int, askSelfTimeout: Timeout, flowRegistryTimeout: Timeout, flowShardingTimeout: Timeout) =
    Props(new HttpService(interface, port, askSelfTimeout, flowRegistryTimeout, flowShardingTimeout))

  private[reactiveflows] def route(
    self:                ActorRef,
    askSelfTimeout:      Timeout,
    flowRegistry:        FlowRegistry,
    flowRegistryTimeout: Timeout,
    flowSharding:        FlowSharding,
    flowShardingTimeout: Timeout
  )(implicit ec: ExecutionContext, fm: FlowMaterializer) = {

    // format: OFF
    def assets = getFromResourceDirectory("web") ~ path("")(getFromResource("web/index.html"))

    def shutdown = path("shutdown") {
      get {
        complete {
          self ! Stop
          "Shutting down now ..."
        }
      }
    }

    def messageEvents = path("message-events") {
      get {
        complete {
          self.ask(CreateMessageEventSource)(askSelfTimeout).mapTo[Source[Flow.MessageEvent]]
        }
      }
    }

    def flows = pathPrefix("flows") {
      path(Segment / "messages") { flowName =>
        get {
          complete {
            implicit val timeout = flowRegistryTimeout
            flowRegistry
              .get(flowName)
              .flatMap { flow =>
              if (flow.isEmpty) Future.successful(StatusCodes.NotFound -> Nil)
              else flowSharding
                .getMessages(flowName)(flowShardingTimeout)
                .map(messages => StatusCodes.OK -> messages)
            }
          }
        } ~
        post {
          entity(as[AddMessageRequest]) { addMessageRequest =>
            complete {
              implicit val timeout = flowRegistryTimeout
              flowRegistry
                .get(flowName)
                .flatMap { flow =>
                if (flow.isEmpty) Future.successful(StatusCodes.NotFound)
                else flowSharding
                  .addMessage(flowName, addMessageRequest.text)(flowShardingTimeout)
                  .map(_ => StatusCodes.Created)
              }
            }
          }
        }
      } ~
      path(Segment) { flowName =>
        delete {
          complete {
            implicit val timeout = flowRegistryTimeout
            flowRegistry.unregister(flowName).map {
              case _: FlowRegistry.FlowUnregistered => StatusCodes.NoContent
              case _: FlowRegistry.UnknownFlow => StatusCodes.NotFound
            }
          }
        }
      } ~
      get {
        complete {
          implicit val timeout = flowRegistryTimeout
          flowRegistry.getAll
        }
      } ~
      post {
        entity(as[AddFlowRequest]) { addFlowRequest =>
          complete {
            implicit val timeout = flowRegistryTimeout
            flowRegistry.register(addFlowRequest.label).map {
              case _: FlowRegistry.FlowRegistered  => StatusCodes.Created
              case _: FlowRegistry.FlowExists => StatusCodes.Conflict
            }
          }
        }
      }
    }

    def flowEvents = path("flow-events") {
      get {
        complete {
          self.ask(CreateFlowEventSource)(askSelfTimeout).mapTo[Source[FlowRegistry.FlowEvent]]
        }
      }
    }
    // format: ON

    assets ~ shutdown ~ messageEvents ~ flows ~ flowEvents
  }
}

/** HTTP service for Reactive Flows. */
class HttpService(interface: String, port: Int, askSelfTimeout: Timeout, flowRegistryTimeout: Timeout, flowShardingTimeout: Timeout)
    extends Actor
    with SettingsActor
    with ActorLogging
    with ImplicitFlowMaterializer {

  import HttpService._
  import context.dispatcher

  Http()(context.system)
    .bind(interface, port)
    .startHandlingWith(route(
      context.self,
      askSelfTimeout,
      FlowRegistry(context.system),
      flowRegistryTimeout,
      FlowSharding(context.system),
      flowShardingTimeout
    ))
  log.info(s"Listening on $interface:$port")
  log.info(s"To shutdown, send GET request to http://$interface:$port/shutdown")

  override def receive = {
    case CreateMessageEventSource => sender() ! createMessageEventSource()
    case CreateFlowEventSource    => sender() ! createFlowEventSource()
    case Stop                     => context.stop(self) // This triggers shutdown by the reaper
  }

  /** Factory for a source of [[Flow.MessageEvent]]. */
  protected def createMessageEventSource() = Source(ActorPublisher(context.actorOf(MessageEventPublisher.props(
    DistributedPubSubExtension(context.system).mediator,
    settings.messageEventPublisher.bufferSize
  ))))

  /** Factory for a source of [[FlowRegistry.FlowEvent]]. */
  protected def createFlowEventSource() = Source(ActorPublisher(context.actorOf(FlowEventPublisher.props(
    DistributedPubSubExtension(context.system).mediator,
    settings.flowEventPublisher.bufferSize
  ))))
}
