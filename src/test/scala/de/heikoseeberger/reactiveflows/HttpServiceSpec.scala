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

import akka.actor.ActorRef
import akka.http.model.{ HttpMethods, HttpRequest, StatusCodes }
import akka.http.testkit.{ RouteTest, TestFrameworkInterface }
import akka.stream.scaladsl.Source
import akka.testkit.{ TestActor, TestProbe }
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.scalatest.{ Matchers, WordSpec }

class HttpServiceSpec extends WordSpec with Matchers with RouteTest with TestFrameworkInterface.Scalatest {

  import HttpService._

  val httpServiceSettings = Settings(system).httpService
  import httpServiceSettings._

  "A HTTP service" should {

    "send itself a Stop message upon receiving a GET request for '/shutdown' and respond with OK" in {
      val httpService = TestProbe()
      val request = HttpRequest(HttpMethods.GET, "/shutdown")
      request ~> route(httpService.ref, askSelfTimeout) ~> check {
        response.status shouldBe StatusCodes.OK
      }
      httpService.expectMsg(Stop)
    }

    "respond with OK and index.html upon receiving a GET request for '/'" in {
      val request = HttpRequest(HttpMethods.GET)
      request ~> route(system.deadLetters, askSelfTimeout) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }

    "respond with OK and index.html upon receiving a GET request for '/index.html'" in {
      val request = HttpRequest(HttpMethods.GET, "/index.html")
      request ~> route(system.deadLetters, askSelfTimeout) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }

    "send itself a CreateMessageEventSource message and respond with OK and an SSE stream upon receiving a GET request for '/message-events'" in {
      val time = LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse("2015-01-01T00:00:00"))
      val httpService = TestProbe()
      httpService.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any) = msg match {
          case CreateMessageEventSource =>
            sender ! Source(List(
              Flow.MessageAdded("akka", Flow.Message("Akka rocks!", time)),
              Flow.MessageAdded("angularjs", Flow.Message("AngularJS rocks!", time))
            ))
            TestActor.NoAutoPilot
        }
      })
      val request = HttpRequest(HttpMethods.GET, "/message-events")
      request ~> route(httpService.ref, askSelfTimeout) ~> check {
        response.status shouldBe StatusCodes.OK
        val expected = """|event:added
                          |data:{
                          |data:  "flowName": "akka",
                          |data:  "message": {
                          |data:    "text": "Akka rocks!",
                          |data:    "dateTime": "2015-01-01T00:00:00"
                          |data:  }
                          |data:}
                          |
                          |event:added
                          |data:{
                          |data:  "flowName": "angularjs",
                          |data:  "message": {
                          |data:    "text": "AngularJS rocks!",
                          |data:    "dateTime": "2015-01-01T00:00:00"
                          |data:  }
                          |data:}
                          |
                          |""".stripMargin
        responseAs[String] shouldBe expected
      }
      httpService.expectMsg(CreateMessageEventSource)
    }
  }
}
