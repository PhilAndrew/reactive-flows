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

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionKey }
import akka.contrib.pattern.DistributedPubSubExtension
import akka.util.Timeout
import scala.concurrent.Future

object FlowSharding extends ExtensionKey[FlowShardingImpl]

/** Communication with sharded [[Flow]]s. */
trait FlowSharding {

  /** Get messages from the flow with the given name. */
  def getMessages(name: String)(implicit timeout: Timeout): Future[Seq[Flow.Message]]

  /** Add a message with the given text to the flow with the given name. */
  def addMessage(name: String, text: String)(implicit timeout: Timeout): Future[Flow.MessageAdded]
}

/** [[FlowSharding]] implementation as an Akka extension. */
class FlowShardingImpl(protected val system: ExtendedActorSystem) extends FlowSharding with Sharding with Extension {

  override def getMessages(name: String)(implicit timeout: Timeout) =
    askEntry(name, Flow.GetMessages).mapTo[Seq[Flow.Message]]

  override def addMessage(name: String, text: String)(implicit timeout: Timeout) =
    askEntry(name, Flow.AddMessage(text)).mapTo[Flow.MessageAdded]

  override protected val typeName = "flow"

  override protected def entryProps = Flow.props(DistributedPubSubExtension(system).mediator)

  override protected def shardCount = Settings(system).flowSharding.shardCount
}
