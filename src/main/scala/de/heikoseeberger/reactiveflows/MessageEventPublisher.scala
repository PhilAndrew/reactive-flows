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

import akka.actor.Props
import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt

object MessageEventPublisher {

  /** Factory for [[MessageEventPublisher]] `Props`. */
  def props(bufferSize: Int) = Props(new MessageEventPublisher(bufferSize))
}

/** A publisher of [[Flow.MessageEvent]]s. */
class MessageEventPublisher(bufferSize: Int) extends EventPublisher[Flow.MessageEvent](bufferSize) {

  import context.dispatcher

  context.system.scheduler.schedule(2 seconds, 2 seconds) {
    self ! Flow.MessageAdded("akka", Flow.Message("Akka and AngularJS are a great combination!", LocalDateTime.now()))
  }

  override protected def receiveEvent = {
    case event: Flow.MessageEvent => onEvent(event)
  }
}
