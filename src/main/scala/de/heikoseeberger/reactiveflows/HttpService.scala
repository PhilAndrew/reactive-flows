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
import akka.stream.scaladsl.ImplicitFlowMaterializer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object HttpService {

  import Directives._

  private[reactiveflows] case object Stop

  /** Name for the [[HttpService]] actor. */
  final val Name = "http-service"

  /** Factory for [[HttpService]] `Props`. */
  def props(interface: String, port: Int) = Props(new HttpService(interface, port))

  private[reactiveflows] def route(self: ActorRef)(implicit ec: ExecutionContext) = {

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
    // format: ON

    assets ~ shutdown
  }
}

/** HTTP service for Reactive Flows. */
class HttpService(interface: String, port: Int) extends Actor with ActorLogging with ImplicitFlowMaterializer {

  import HttpService._
  import context.dispatcher

  Http()(context.system)
    .bind(interface, port)
    .startHandlingWith(route(context.self))
  log.info(s"Listening on $interface:$port")
  log.info(s"To shutdown, send GET request to http://$interface:$port/shutdown")

  override def receive = {
    case Stop => context.stop(self) // This triggers shutdown by the reaper
  }
}
