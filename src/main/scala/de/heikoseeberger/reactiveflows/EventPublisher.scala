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

import akka.stream.actor.{ ActorPublisher, ActorPublisherMessage }

/** Base class for an actor publishing events. */
abstract class EventPublisher[A](bufferSize: Int) extends ActorPublisher[A] {

  private var events = Vector.empty[A]

  /** Receive events and `ActorPublisherMessage`s. */
  final override def receive = receiveEvent.orElse {
    case ActorPublisherMessage.Request(demand) => publish(demand)
    case _: ActorPublisherMessage              => context.stop(self)
  }

  /** To be implemented by invoking [[onEvent]]. */
  protected def receiveEvent: Receive

  /** To be invoked when an event is received. */
  final protected def onEvent(event: A): Unit = {
    events = (events :+ event).takeRight(bufferSize)
    if (isActive) publish(totalDemand)
  }

  private def publish(demand: Long) = {
    val (requested, remaining) = events.splitAt(demand.toInt)
    requested.foreach(onNext)
    events = remaining
  }
}
