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

import de.heikoseeberger.akkasse.ServerSentEvent
import spray.json.{ PrettyPrinter, jsonWriter }

object ServerSentEventProtocol extends ServerSentEventProtocol

/** Server-sent events conversions. */
trait ServerSentEventProtocol {

  import JsonProtocol._

  /** Converts a [[Flow.MessageEvent]] to a `ServerSentEvent`. */
  implicit def messageEventToServerSentEvent(event: Flow.MessageEvent): ServerSentEvent =
    event match {
      case messageAdded: Flow.MessageAdded =>
        val data = PrettyPrinter(jsonWriter[Flow.MessageAdded].write(messageAdded))
        ServerSentEvent(data, "added")
    }

  /** Converts a [[FlowRegistry.FlowEvent]] to a `ServerSentEvent`. */
  implicit def flowEventToServerSentEvent(event: FlowRegistry.FlowEvent): ServerSentEvent =
    event match {
      case FlowRegistry.FlowRegistered(flow) =>
        val data = PrettyPrinter(jsonWriter[FlowRegistry.Flow].write(flow))
        ServerSentEvent(data, "added")
      case FlowRegistry.FlowUnregistered(name) =>
        ServerSentEvent(name, "removed")
    }
}
