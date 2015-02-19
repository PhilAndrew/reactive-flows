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

import akka.cluster.Cluster
import akka.util.Timeout
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class FlowShardingSpec extends BaseAkkaSpec {

  "FlowSharding" should {

    Cluster(system).join(Cluster(system).selfAddress)
    val flowSharding = FlowSharding(system)
    flowSharding.start()

    "correctly handle invocations of getMessages and addMessage" in {
      implicit val timeout = 5 seconds: Timeout // The cluster has to start ...
      Await.result(flowSharding.getMessages("akka"), timeout.duration) shouldBe Nil
      val messageAdded = Await.result(flowSharding.addMessage("akka", "Akka rocks!"), timeout.duration)
      messageAdded.flowName shouldBe "akka"
      messageAdded.message.text shouldBe "Akka rocks!"
    }
  }
}
