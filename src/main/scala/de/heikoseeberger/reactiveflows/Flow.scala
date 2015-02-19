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
import akka.persistence.PersistentActor
import java.time.LocalDateTime

object Flow {

  /** Supertype for all message events. */
  sealed trait MessageEvent

  /** Command to get messages. */
  case object GetMessages

  /** Command to add a message with the given text. */
  case class AddMessage(text: String)

  /** [[MessageEvent]] signaling that the given [[Message]] has been added to the given flow. */
  case class MessageAdded(flowName: String, message: Message) extends MessageEvent

  /** A message with text and date-time. */
  case class Message(text: String, dateTime: LocalDateTime)

  /** Distributed pub-sub key for [[MessageEvent]]s .*/
  final val MessageEventKey = "message-events"

  /** Factory for [[Flow]] `Props`. */
  def props(mediator: ActorRef) = Props(new Flow(mediator))
}

/** A flow holds [[Flow.Message]]s. */
class Flow(mediator: ActorRef) extends PersistentActor with ActorLogging {

  import Flow._

  private val name = self.path.name

  private var messages = List.empty[Message]

  log.debug("Flow '{}' created", name)

  override def persistenceId = s"flow-$name"

  // Command handling

  override def receiveCommand = {
    case GetMessages      => getMessages()
    case AddMessage(text) => addMessage(text)
  }

  private def getMessages() = sender() ! messages

  private def addMessage(text: String) =
    persist(MessageAdded(name, Message(text, LocalDateTime.now()))) { messageAdded =>
      updateState(messageAdded)
      mediator ! DistributedPubSubMediator.Publish(MessageEventKey, messageAdded)
      sender() ! messageAdded
    }

  // Event handling

  override def receiveRecover = {
    case messageAdded: MessageAdded => updateState(messageAdded)
  }

  private def updateState(event: MessageEvent) = {
    def onMessageAdded(flowName: String, message: Message) = {
      messages +:= message
      log.info("Added message {} to flow '{}'", message, flowName)
    }
    event match {
      case MessageAdded(flowName, message) => onMessageAdded(flowName, message)
    }
  }
}
