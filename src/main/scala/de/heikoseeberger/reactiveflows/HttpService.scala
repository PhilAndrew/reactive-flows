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
import akka.http.Http
import akka.http.server.Directives
import akka.pattern.ask
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{ ImplicitFlowMaterializer, Source }
import akka.util.Timeout
import de.heikoseeberger.akkasse.EventStreamMarshalling
import scala.concurrent.ExecutionContext

object HttpService {

  import Directives._
  import EventStreamMarshalling._
  import ServerSentEventProtocol._

  private[reactiveflows] case object Stop

  private[reactiveflows] case object CreateMessageEventSource

  /** Name for the [[HttpService]] actor. */
  final val Name = "http-service"

  /** Factory for [[HttpService]] `Props`. */
  def props(interface: String, port: Int, askSelfTimeout: Timeout) =
    Props(new HttpService(interface, port, askSelfTimeout))

  private[reactiveflows] def route(self: ActorRef, askSelfTimeout: Timeout)(implicit ec: ExecutionContext) = {

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
    // format: ON

    assets ~ shutdown ~ messageEvents
  }
}

/** HTTP service for Reactive Flows. */
class HttpService(interface: String, port: Int, askSelfTimeout: Timeout)
    extends Actor
    with SettingsActor
    with ActorLogging
    with ImplicitFlowMaterializer {

  import HttpService._
  import context.dispatcher

  Http()(context.system)
    .bind(interface, port)
    .startHandlingWith(route(context.self, askSelfTimeout))
  log.info(s"Listening on $interface:$port")
  log.info(s"To shutdown, send GET request to http://$interface:$port/shutdown")

  override def receive = {
    case CreateMessageEventSource => sender() ! createMessageEventSource()
    case Stop                     => context.stop(self) // This triggers shutdown by the reaper
  }

  /** Factory for a source of [[Flow.MessageEvent]]. */
  protected def createMessageEventSource() = Source(ActorPublisher(context.actorOf(
    MessageEventPublisher.props(settings.messageEventPublisher.bufferSize)
  )))
}
