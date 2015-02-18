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

import akka.http.marshalling.{ PredefinedToEntityMarshallers, ToEntityMarshaller }
import akka.http.model.{ MediaRange, MediaTypes }
import akka.http.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import akka.http.unmarshalling.{ FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers }
import akka.http.util.FastFuture
import akka.stream.FlowMaterializer
import scala.concurrent.ExecutionContext
import spray.json.{ JsonParser, JsonPrinter, PrettyPrinter, RootJsonReader, RootJsonWriter }

object JsonMarshalling extends JsonMarshalling

trait JsonMarshalling {

  implicit def unmarshaller[A](implicit reader: RootJsonReader[A], ec: ExecutionContext, mat: FlowMaterializer): FromEntityUnmarshaller[A] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.flatMapWithInput { (entity, s) =>
      if (entity.contentType.mediaType == MediaTypes.`application/json`)
        FastFuture.successful(reader.read(JsonParser(s)))
      else
        FastFuture.failed(UnsupportedContentTypeException(MediaRange(MediaTypes.`application/json`)))
    }

  implicit def marshaller[A](implicit writer: RootJsonWriter[A], printer: JsonPrinter = PrettyPrinter): ToEntityMarshaller[A] =
    PredefinedToEntityMarshallers.stringMarshaller(MediaTypes.`application/json`)
      .compose(printer)
      .compose(writer.write)
}
