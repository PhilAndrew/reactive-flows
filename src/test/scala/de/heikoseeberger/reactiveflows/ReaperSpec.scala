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

import akka.actor.ActorDSL.{ Act, actor }
import akka.testkit.TestProbe

class ReaperSpec extends BaseAkkaSpec {

  "A reaper" should {

    "shutdown the actor system upon termination of the HTTP service" in {
      val shutdownDelegate = TestProbe()
      val httpService = actor(new Act {
        become { case "stop" => context.stop(self) }
      })
      actor(new Reaper {
        override protected def createHttpService() = httpService
        override protected def shutdown() = shutdownDelegate.ref ! "shutdown"
      })
      httpService ! "stop"
      shutdownDelegate.expectMsg("shutdown")
    }
  }
}
