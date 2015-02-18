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

import de.heikoseeberger.akkasse.ServerSentEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.scalatest.{ Matchers, WordSpec }

class ServerSentEventProtocolSpec extends WordSpec with Matchers {

  import ServerSentEventProtocol._

  "A MessageEvent" should {

    "be converted to a ServerSentEvent" in {
      val time = LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse("2015-01-01T00:00:00"))
      val serverSentEvent = Flow.MessageAdded("akka", Flow.Message("Akka rocks!", time)): ServerSentEvent
      val expectedData = """|{
                            |  "flowName": "akka",
                            |  "message": {
                            |    "text": "Akka rocks!",
                            |    "dateTime": "2015-01-01T00:00:00"
                            |  }
                            |}""".stripMargin
      serverSentEvent.data shouldBe expectedData
      serverSentEvent.eventType shouldBe Some("added")
    }
  }

  "A FlowEvent" should {

    "be converted to a ServerSentEvent, if it is a FlowRegistered" in {
      val serverSentEvent = FlowRegistry.FlowRegistered(FlowRegistry.Flow("akka", "Akka")): ServerSentEvent
      val expectedData = """|{
                            |  "name": "akka",
                            |  "label": "Akka"
                            |}""".stripMargin
      serverSentEvent.data shouldBe expectedData
      serverSentEvent.eventType shouldBe Some("added")
    }

    "be converted to a ServerSentEvent, if it is a FlowUnregistered" in {
      val serverSentEvent = FlowRegistry.FlowUnregistered("akka"): ServerSentEvent
      serverSentEvent.data shouldBe "akka"
      serverSentEvent.eventType shouldBe Some("removed")
    }
  }
}
