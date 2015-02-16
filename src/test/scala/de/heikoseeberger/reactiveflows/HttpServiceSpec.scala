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

import akka.http.model.{ HttpMethods, HttpRequest, StatusCodes }
import akka.http.testkit.{ RouteTest, TestFrameworkInterface }
import akka.testkit.TestProbe
import org.scalatest.{ Matchers, WordSpec }

class HttpServiceSpec extends WordSpec with Matchers with RouteTest with TestFrameworkInterface.Scalatest {

  import HttpService._

  "A HTTP service" should {

    "send itself a Stop message upon receiving a GET request for '/shutdown' and respond with OK" in {
      val httpService = TestProbe()
      val request = HttpRequest(HttpMethods.GET, "/shutdown")
      request ~> route(httpService.ref) ~> check {
        response.status shouldBe StatusCodes.OK
      }
      httpService.expectMsg(Stop)
    }

    "respond with OK and index.html upon receiving a GET request for '/'" in {
      val request = HttpRequest(HttpMethods.GET)
      request ~> route(system.deadLetters) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }

    "respond with OK and index.html upon receiving a GET request for '/index.html'" in {
      val request = HttpRequest(HttpMethods.GET, "/index.html")
      request ~> route(system.deadLetters) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }
  }
}