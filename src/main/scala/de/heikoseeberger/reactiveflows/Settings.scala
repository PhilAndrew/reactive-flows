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

import akka.actor.{ Actor, ExtendedActorSystem, Extension, ExtensionKey }
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

object Settings extends ExtensionKey[Settings]

/** Configuration settings for Reactive Flows. */
class Settings(system: ExtendedActorSystem) extends Extension {

  /** http-service namespace. */
  object httpService {

    /** Timeout for the [[HttpService]] asking itself. */
    val askSelfTimeout = getDuration("http-service.ask-self-timeout")

    /** Timeout for the [[HttpService]] interacting with the [[FlowRegistry]]. */
    val flowRegistryTimeout = getDuration("http-service.flow-registry-timeout")

    /** Interface for the [[HttpService]] to bind to. */
    val interface = reactiveFlows.getString("http-service.interface")

    /** Port for the [[HttpService]] to bind to. */
    val port = reactiveFlows.getInt("http-service.port")
  }

  /** message-event-publisher namespace. */
  object messageEventPublisher {

    /** Buffer size for the [[MessageEventPublisher]]. */
    val bufferSize = reactiveFlows.getInt("message-event-publisher.buffer-size")
  }

  /** flow-event-publisher namespace. */
  object flowEventPublisher {

    /** Buffer size for the [[FlowEventPublisher]]. */
    val bufferSize = reactiveFlows.getInt("flow-event-publisher.buffer-size")
  }

  /** flow-registry namespace. */
  object flowRegistry {

    /** Timeout for read consistency. */
    val readTimeout = getDuration("flow-registry.read-timeout")

    /** Timeout for write consistency. */
    val writeTimeout = getDuration("flow-registry.write-timeout")
  }

  private val reactiveFlows = system.settings.config.getConfig("reactive-flows")

  private def getDuration(key: String) = FiniteDuration(reactiveFlows.getDuration(key, MILLISECONDS), MILLISECONDS)
}

/** Convenient access to configuration settings for actors. */
trait SettingsActor {
  this: Actor =>

  /** Configuration settings for Reactive Flows. */
  val settings = Settings(context.system)
}
