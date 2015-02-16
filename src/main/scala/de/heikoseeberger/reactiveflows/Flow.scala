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

object Flow {

  /** Supertype for all message events. */
  sealed trait MessageEvent

  /** [[MessageEvent]] signaling that the given [[Message]] has been added to the given flow. */
  case class MessageAdded(flowName: String, message: Message) extends MessageEvent

  /** A message with text and date-time. */
  case class Message(text: String, dateTime: LocalDateTime)
}
