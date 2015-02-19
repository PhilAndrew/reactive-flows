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

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionKey }
import akka.cluster.Cluster
import akka.contrib.datareplication.{ DataReplication, ORSet, Replicator }
import akka.contrib.pattern.{ DistributedPubSubExtension, DistributedPubSubMediator }
import akka.pattern.ask
import akka.util.Timeout
import java.net.URLEncoder
import scala.concurrent.{ ExecutionContext, Future }

object FlowRegistry extends ExtensionKey[FlowRegistryImpl] {

  /** Supertype for all flow events. */
  sealed trait FlowEvent

  /** Entities managed by a [[FlowRegistry]]. */
  case class Flow(name: String, label: String)

  /** Supertype for results of [[FlowRegistry.register]]. */
  sealed trait RegisterFlowResult
  /** Signals successful registration of the given [[Flow]]. */
  case class FlowRegistered(flow: Flow) extends RegisterFlowResult with FlowEvent
  /** Signals that the [[Flow]] with the given name can't be registered, because it already exists. */
  case class FlowExists(label: String) extends Exception(s"Flow with label '$label' exists!") with RegisterFlowResult

  /** Supertype for results of [[FlowRegistry.unregister]]. */
  sealed trait UnregisterFlowReponse
  /** Signals successful unregistration of the [[Flow]] with the given naem. */
  case class FlowUnregistered(name: String) extends UnregisterFlowReponse with FlowEvent
  /** Signals that the [[Flow]] with the given name can't be unregistered, because it is unknown. */
  case class UnknownFlow(name: String) extends Exception(s"Flow with name '$name' unknown!") with UnregisterFlowReponse

  /** Distributed pub-sub key for [[FlowEvent]]s .*/
  final val FlowEventKey = "flow-events"
}

/** A registry for flows represented as [[FlowRegistry.Flow]], i.e. flow names and labels. */
trait FlowRegistry {

  import FlowRegistry._

  /** Get all flows. */
  def getAll(implicit timeout: Timeout, ec: ExecutionContext): Future[Set[Flow]]

  /** Get the flow with the given name. */
  def get(name: String)(implicit timeout: Timeout, ec: ExecutionContext): Future[Option[Flow]]

  /** Register the given flow. */
  def register(label: String)(implicit timeout: Timeout, ec: ExecutionContext): Future[RegisterFlowResult]

  /** Unregister the flove with the given name. */
  def unregister(name: String)(implicit timeout: Timeout, ec: ExecutionContext): Future[UnregisterFlowReponse]
}

object FlowRegistryImpl {
  private final val ReplicatorKey = "flows"
}

/** [[FlowRegistry]] implementation as an Akka extension. */
class FlowRegistryImpl(system: ExtendedActorSystem) extends FlowRegistry with Extension {

  import FlowRegistry._
  import FlowRegistryImpl._

  private implicit val node = Cluster(system)

  private val replicator = DataReplication(system).replicator

  private val mediator = DistributedPubSubExtension(system).mediator

  private val settings = Settings(system).flowRegistry

  override def getAll(implicit timeout: Timeout, ec: ExecutionContext) =
    replicator
      .ask(get)
      .mapTo[Replicator.GetResponse]
      .flatMap {
        case Replicator.GetSuccess(ReplicatorKey, ORSet(flows), _) =>
          Future.successful(flows.asInstanceOf[Set[Flow]])
        case Replicator.NotFound(ReplicatorKey, _) =>
          Future.successful(Set.empty[Flow])
        case other =>
          Future.failed(new Exception(s"Error getting all flows: $other"))
      }

  override def get(name: String)(implicit timeout: Timeout, ec: ExecutionContext) =
    replicator
      .ask(get)
      .mapTo[Replicator.GetResponse]
      .flatMap {
        case Replicator.GetSuccess(ReplicatorKey, ORSet(flows), _) =>
          Future.successful(flows.asInstanceOf[Set[Flow]].find(_.name == name))
        case Replicator.NotFound(ReplicatorKey, _) =>
          Future.successful(None)
        case other =>
          Future.failed(new Exception(s"Error finding flow with name '$name': $other"))
      }

  override def register(label: String)(implicit timeout: Timeout, ec: ExecutionContext) = {
    val flow = Flow(URLEncoder.encode(label.toLowerCase, "UTF-8"), label)
    def checkAndAdd(flows: ORSet[Flow]) = if (flows.contains(flow)) throw FlowExists(flow.label) else flows + flow
    replicator
      .ask(update(checkAndAdd))
      .mapTo[Replicator.UpdateResponse]
      .flatMap {
        case Replicator.UpdateSuccess(ReplicatorKey, _) =>
          val flowRegistered = FlowRegistered(flow)
          mediator ! DistributedPubSubMediator.Publish(FlowEventKey, flowRegistered)
          Future.successful(flowRegistered)
        case Replicator.ModifyFailure(ReplicatorKey, _, flowExists: FlowExists, _) =>
          Future.successful(flowExists)
        case other =>
          Future.failed(new Exception(s"Error registereing flow with label '$label': $other"))
      }
  }

  override def unregister(name: String)(implicit timeout: Timeout, ec: ExecutionContext) = {
    def checkAndRemove(flows: ORSet[Flow]) = flows.elements
      .find(_.name == name)
      .fold(throw UnknownFlow(name))(flows - _)
    replicator
      .ask(update(checkAndRemove))
      .mapTo[Replicator.UpdateResponse]
      .flatMap {
        case Replicator.UpdateSuccess(ReplicatorKey, _) =>
          val flowUnregistered = FlowUnregistered(name)
          mediator ! DistributedPubSubMediator.Publish(FlowEventKey, flowUnregistered)
          Future.successful(flowUnregistered)
        case Replicator.ModifyFailure(ReplicatorKey, _, unknownFlow: UnknownFlow, _) =>
          Future.successful(unknownFlow)
        case other =>
          Future.failed(new Exception(s"Error unregistering flow with name '$name': $other"))
      }
  }

  private def get = Replicator.Get(ReplicatorKey, Replicator.ReadQuorum(settings.readTimeout))

  private def update(modify: ORSet[Flow] => ORSet[Flow]) =
    Replicator.Update(
      ReplicatorKey,
      ORSet.empty[Flow],
      Replicator.ReadQuorum(settings.readTimeout),
      Replicator.WriteQuorum(settings.writeTimeout)
    )(modify)
}
