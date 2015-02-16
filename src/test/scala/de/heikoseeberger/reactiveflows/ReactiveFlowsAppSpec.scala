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

import org.scalatest.{ Matchers, WordSpec }

class ReactiveFlowsAppSpec extends WordSpec with Matchers {

  import ReactiveFlowsApp._

  "applySystemProperties" should {

    "set options prefixed with -D as system properties" in {
      val oldProperties = System.getProperties
      try {
        System.setProperty("foo", "undefined")
        System.setProperty("bar", "undefined")
        System.setProperty("baz", "undefined")
        applySystemProperties(List("foo", "bar=bar", "-Dbaz=baz"))
        System.getProperty("foo") shouldBe "undefined"
        System.getProperty("bar") shouldBe "undefined"
        System.getProperty("baz") shouldBe "baz"
      } finally
        System.setProperties(oldProperties)
    }
  }
}
