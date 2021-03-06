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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import spray.json.{ DefaultJsonProtocol, JsString, JsValue, RootJsonFormat }

object JsonProtocol extends JsonProtocol

/** JSON formats for (de)serialization. */
trait JsonProtocol extends DefaultJsonProtocol {

  /** JSON formt for LocalDateTime. */
  implicit object LocalDateTimeFormat extends RootJsonFormat[LocalDateTime] {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override def write(localDateTime: LocalDateTime) = JsString(formatter.format(localDateTime))

    override def read(json: JsValue) = json match {
      case JsString(s) => LocalDateTime.from(formatter.parse(s))
      case _           => throw new IllegalArgumentException(s"JsString expected: $json")
    }
  }

  /** JSON format for [[Flow.Message]]. */
  implicit val messageFormat = jsonFormat2(Flow.Message)

  /** JSON format for [[Flow.MessageAdded]]. */
  implicit val messageAddedFormat = jsonFormat2(Flow.MessageAdded)

  /** JSON format for [[FlowRegistry.Flow]]. */
  implicit val flowFormat = jsonFormat2(FlowRegistry.Flow.apply)
}
