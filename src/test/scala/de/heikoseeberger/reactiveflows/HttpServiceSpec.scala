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
import akka.http.model.{ HttpEntity, HttpMethods, HttpRequest, StatusCodes }
import akka.http.model.ContentTypes.`application/json`
import akka.http.testkit.{ RouteTest, TestFrameworkInterface }
import akka.stream.scaladsl.Source
import akka.testkit.{ TestActor, TestProbe }
import akka.util.Timeout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }
import scala.concurrent.{ ExecutionContext, Future }

class HttpServiceSpec extends WordSpec
    with Matchers
    with MockFactory
    with RouteTest
    with TestFrameworkInterface.Scalatest {

  import HttpService._

  val httpServiceSettings = Settings(system).httpService
  import httpServiceSettings._

  "A HTTP service" should {

    "send itself a Stop message upon receiving a GET request for '/shutdown' and respond with OK" in {
      val httpService = TestProbe()
      val request = HttpRequest(HttpMethods.GET, "/shutdown")
      request ~> route(httpService.ref, askSelfTimeout, mock[FlowRegistry], flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
        response.status shouldBe StatusCodes.OK
      }
      httpService.expectMsg(Stop)
    }

    "respond with OK and index.html upon receiving a GET request for '/'" in {
      val request = HttpRequest(HttpMethods.GET)
      request ~> route(system.deadLetters, askSelfTimeout, mock[FlowRegistry], flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }

    "respond with OK and index.html upon receiving a GET request for '/index.html'" in {
      val request = HttpRequest(HttpMethods.GET, "/index.html")
      request ~> route(system.deadLetters, askSelfTimeout, mock[FlowRegistry], flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
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
      request ~> route(httpService.ref, askSelfTimeout, mock[FlowRegistry], flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
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

  "call FlowRegistry.getAll and respond with OK and all flows upon receiving a GET request for '/flows'" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.getAll(_: Timeout, _: ExecutionContext)).expects(*, *).returns(
      Future.successful(Set(FlowRegistry.Flow("akka", "Akka"), FlowRegistry.Flow("angularjs", "AngularJS")))
    )
    val request = HttpRequest(HttpMethods.GET, "/flows")
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.OK
      responseAs[String] shouldBe """|[{
                                     |  "name": "akka",
                                     |  "label": "Akka"
                                     |}, {
                                     |  "name": "angularjs",
                                     |  "label": "AngularJS"
                                     |}]""".stripMargin
    }
  }

  "call FlowRegistry.add and respond with Created upon receiving a POST request for '/flows' with a fresh label" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.register(_: String)(_: Timeout, _: ExecutionContext)).expects("Akka", *, *).returns(
      Future.successful(FlowRegistry.FlowRegistered(FlowRegistry.Flow("akka", "Akka")))
    )
    val request = HttpRequest(
      HttpMethods.POST,
      "/flows",
      entity = HttpEntity(`application/json`, """{ "label": "Akka" }""")
    )
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.Created
    }
  }

  "call FlowRegistry.add and respond with Conflict upon receiving a POST request for '/flows' with an existing label" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.register(_: String)(_: Timeout, _: ExecutionContext)).expects("Akka", *, *).returns(
      Future.successful(FlowRegistry.FlowExists("akka"))
    )
    val request = HttpRequest(
      HttpMethods.POST,
      "/flows",
      entity = HttpEntity(`application/json`, """{ "label": "Akka" }""")
    )
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.Conflict
    }
  }

  "call FlowRegistry.remove and respond with NoContent upon receiving a DELETE request for '/flows' with an existing name" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.unregister(_: String)(_: Timeout, _: ExecutionContext)).expects("akka", *, *).returns(
      Future.successful(FlowRegistry.FlowUnregistered("akka"))
    )
    val request = HttpRequest(HttpMethods.DELETE, "/flows/akka")
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.NoContent
    }
  }

  "call FlowRegistry.remove and respond with NotFound upon receiving a DELETE request for '/flows' with an unknown name" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.unregister(_: String)(_: Timeout, _: ExecutionContext)).expects("akka", *, *).returns(
      Future.successful(FlowRegistry.UnknownFlow("akka"))
    )
    val request = HttpRequest(HttpMethods.DELETE, "/flows/akka")
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }

  "send itself a CreateFlowEventSource message and respond with OK and an SSE stream upon receiving a GET request for '/flow-events'" in {
    val time = LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse("2015-01-01T00:00:00"))
    val httpService = TestProbe()
    httpService.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any) = msg match {
        case CreateFlowEventSource =>
          sender ! Source(List(
            FlowRegistry.FlowRegistered(FlowRegistry.Flow("akka", "Akka")),
            FlowRegistry.FlowUnregistered("angularjs")
          ))
          TestActor.NoAutoPilot
      }
    })
    val request = HttpRequest(HttpMethods.GET, "/flow-events")
    request ~> route(httpService.ref, askSelfTimeout, mock[FlowRegistry], flowRegistryTimeout, mock[FlowSharding], flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.OK
      val expected = """|event:added
                        |data:{
                        |data:  "name": "akka",
                        |data:  "label": "Akka"
                        |data:}
                        |
                        |event:removed
                        |data:angularjs
                        |
                        |""".stripMargin
      responseAs[String] shouldBe expected
    }
    httpService.expectMsg(CreateFlowEventSource)
  }

  "call FlowRegistry.get, FlowSharding.getMessages, and respond with OK and all messages of the akka flow upon receiving a GET request for '/flows/akka/messages'" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.get(_: String)(_: Timeout, _: ExecutionContext)).expects("akka", *, *).returns(
      Future.successful(Some(FlowRegistry.Flow("akka", "Akka")))
    )
    val time = LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse("2015-01-01T00:00:00"))
    val flowSharding = mock[FlowSharding]
    (flowSharding.getMessages(_: String)(_: Timeout)).expects("akka", *).returns(
      Future.successful(List(Flow.Message("Akka rocks!", time)))
    )
    val request = HttpRequest(HttpMethods.GET, "/flows/akka/messages")
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, flowSharding, flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.OK
      responseAs[String] shouldBe """|[{
                                     |  "text": "Akka rocks!",
                                     |  "dateTime": "2015-01-01T00:00:00"
                                     |}]""".stripMargin
    }
  }

  "call FlowRegistry.get and respond with NotFound upon receiving a GET request for '/flows/unknown/messages'" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.get(_: String)(_: Timeout, _: ExecutionContext)).expects("unknown", *, *).returns(
      Future.successful(None)
    )
    val flowSharding = mock[FlowSharding]
    (flowSharding.getMessages(_: String)(_: Timeout)).expects(*, *).never()
    val request = HttpRequest(HttpMethods.GET, "/flows/unknown/messages")
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, flowSharding, flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }

  "call FlowRegistry.get, FlowSharding.addMessage, and respond with Created upon receiving a POST request for '/flows/akka/messages'" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.get(_: String)(_: Timeout, _: ExecutionContext)).expects("akka", *, *).returns(
      Future.successful(Some(FlowRegistry.Flow("akka", "Akka")))
    )
    val time = LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse("2015-01-01T00:00:00"))
    val flowSharding = mock[FlowSharding]
    (flowSharding.addMessage(_: String, _: String)(_: Timeout)).expects("akka", "Akka rocks!", *).returns(
      Future.successful(Flow.MessageAdded("akka", (Flow.Message("Akka rocks!", time))))
    )
    val request = HttpRequest(
      HttpMethods.POST,
      "/flows/akka/messages",
      entity = HttpEntity(`application/json`, """{ "text": "Akka rocks!" }""")
    )
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, flowSharding, flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.Created
    }
  }

  "call FlowRegistry.get and respond with NotFound upon receiving a POST request for '/flows/unknown/messages'" in {
    val flowRegistry = mock[FlowRegistry]
    (flowRegistry.get(_: String)(_: Timeout, _: ExecutionContext)).expects("unknown", *, *).returns(
      Future.successful(None)
    )
    val flowSharding = mock[FlowSharding]
    (flowSharding.getMessages(_: String)(_: Timeout)).expects(*, *).never()
    val request = HttpRequest(
      HttpMethods.POST,
      "/flows/unknown/messages",
      entity = HttpEntity(`application/json`, """{ "text": "Unknown sucks!" }""")
    )
    request ~> route(system.deadLetters, askSelfTimeout, flowRegistry, flowRegistryTimeout, flowSharding, flowShardingTimeout) ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }
}
