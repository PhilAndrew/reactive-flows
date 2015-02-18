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

import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class FlowRegistrySpec extends BaseAkkaSpec {

  import FlowRegistry._
  import system.dispatcher

  val flowRegistry = FlowRegistry(system)
  implicit val flowRegistryTimeout = 1 second: Timeout
  val awaitTimeout = 2 seconds

  "A FlowRegistry" should {
    "correctly handle getting, registering and unregistering flows" in {
      Await.result(flowRegistry.getAll, awaitTimeout) shouldBe Set.empty
      Await.result(flowRegistry.register("Akka"), awaitTimeout) shouldBe FlowRegistered(Flow("akka", "Akka"))
      Await.result(flowRegistry.getAll, awaitTimeout) shouldBe Set(Flow("akka", "Akka"))
      Await.result(flowRegistry.register("Akka"), awaitTimeout) shouldBe FlowExists("Akka")
      Await.result(flowRegistry.unregister("angularjs"), awaitTimeout) shouldBe UnknownFlow("angularjs")
      Await.result(flowRegistry.unregister("akka"), awaitTimeout) shouldBe FlowUnregistered("akka")
      Await.result(flowRegistry.getAll, awaitTimeout) shouldBe Set.empty
    }
  }
}
