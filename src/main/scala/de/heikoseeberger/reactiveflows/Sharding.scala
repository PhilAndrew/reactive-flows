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

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.contrib.pattern.{ ClusterSharding, ShardRegion }
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future

/** Utility for setting up and using sharding for a particular entry type. */
trait Sharding {

  private final val sharding = ClusterSharding(system)

  /** Starts sharding with [[typeName]], [[entryProps]]. */
  def start(): Unit = {
    Logging(system, getClass).info("Starting shard region for type '{}'", typeName)
    sharding.start(typeName, Some(entryProps), idExtractor, shardResolver(shardCount))
  }

  /** Ask the entry with the given name the given message. */
  def askEntry(name: String, message: Any)(implicit timeout: Timeout): Future[Any] =
    shardRegion ? (name, message)

  /** Actor system needed to obtain the `ClusterSharding` extension. */
  protected def system: ActorSystem

  /** Type name for entries. */
  protected def typeName: String

  /** `Props` for entries. */
  protected def entryProps: Props

  /** Shard count; should be configurable. */
  protected def shardCount: Int

  private def idExtractor: ShardRegion.IdExtractor = {
    case (name: String, payload) => (name, payload)
  }

  private def shardResolver(shardCount: Int): ShardRegion.ShardResolver = {
    case (name: String, _) => (name.hashCode % shardCount).toString
  }

  private def shardRegion = sharding.shardRegion(typeName)
}
