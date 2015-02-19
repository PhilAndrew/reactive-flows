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

import akka.actor.{ OneForOneStrategy, Actor, ActorLogging, ActorRef, Props, SupervisorStrategy, Terminated }

object Reaper {

  /** Name for the [[Reaper]] actor. */
  final val Name = "reaper"

  /** Factory for [[Reaper]] `Props`. */
  def props = Props(new Reaper)
}

/** Single top-level actor. */
class Reaper extends Actor with ActorLogging with SettingsActor {

  /** Try to restart faulty child actors up to 3 times. */
  override val supervisorStrategy = OneForOneStrategy(3)(SupervisorStrategy.defaultDecider)

  context.watch(createHttpService())

  /** Upon termination of a child actor shutdown the actor system. */
  override def receive = {
    case Terminated(actorRef) =>
      log.warning("Shutting down, because {} has terminated!", actorRef.path)
      shutdown()
  }

  /** Factory for the [[HttpService]] actor. */
  protected def createHttpService() = context.actorOf(
    HttpService.props(
      settings.httpService.interface,
      settings.httpService.port,
      settings.httpService.askSelfTimeout,
      settings.httpService.flowRegistryTimeout,
      settings.httpService.flowShardingTimeout
    ),
    HttpService.Name
  )

  /** Shutdown the actor system. */
  protected def shutdown() = context.system.shutdown()
}
