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

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.event.Logging

/** Main entry point for Reactive Flows. */
object ReactiveFlowsApp {

  private val opt = """-D(\S+)=(\S+)""".r

  def main(args: Array[String]): Unit = {
    applySystemProperties(args.toList)

    val system = ActorSystem("reactive-flows")
    val log = Logging.apply(system, getClass)

    log.debug("Waiting to become a cluster member ...")
    Cluster(system).registerOnMemberUp {
      system.actorOf(Reaper.props, Reaper.Name)
      log.info("Reactive Flows up and running")
    }

    system.awaitTermination()
  }

  private[reactiveflows] def applySystemProperties(args: Seq[String]) =
    for (opt(key, value) <- args) System.setProperty(key, value)
}
