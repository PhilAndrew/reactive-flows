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

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.contrib.pattern.DistributedPubSubMediator
import de.heikoseeberger.akkasse.stream.EventPublisher

object FlowEventPublisher {

  /** Factory for [[FlowEventPublisher]] `Props`. */
  def props(mediator: ActorRef, bufferSize: Int) = Props(new FlowEventPublisher(mediator, bufferSize))
}

/** A publisher of [[FlowRegistry.FlowEvent]]s. */
class FlowEventPublisher(mediator: ActorRef, bufferSize: Int)
    extends EventPublisher[FlowRegistry.FlowEvent](bufferSize)
    with ActorLogging {

  mediator ! DistributedPubSubMediator.Subscribe(FlowRegistry.FlowEventKey, self)
  log.debug("Subscribed to flow events")

  override def receiveEvent = {
    case event: FlowRegistry.FlowEvent => onEvent(event)
  }
}
