/*
 * Copyright (c) 2019 Couchbase, Inc.
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

package com.couchbase.client.scala

import com.couchbase.client.core.msg.kv.GetRequest
import com.couchbase.client.scala.codec.JsonSerializer
import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.durability.Durability._
import com.couchbase.client.scala.kv._
import com.couchbase.client.scala.util.{FutureConversions, TimeoutUtil}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** Provides asynchronous access to all collection APIs, based around reactive programming using the
  * [[https://projectreactor.io/ Project Reactor]] library.  This is the main entry-point
  * for key-value (KV) operations.
  *
  * <p>If synchronous, blocking access is needed, we recommend looking at the [[Collection]].  If a simpler
  * async API based around Scala `Future`s is desired, then check out the [[AsyncCollection]].
  *
  * @author Graham Pople
  * @since 1.0.0
  * @define Same             This reactive programming version performs the same functionality and takes the same
  *                          parameters,
  *                          but returns the same result object asynchronously in a Project Reactor `SMono`.
  * */
class ReactiveCollection(async: AsyncCollection) {
  private[scala] val kvTimeout: Durability => Duration = TimeoutUtil.kvTimeout(async.environment)
  private[scala] val kvReadTimeout: Duration           = async.kvReadTimeout
  private val environment                              = async.environment
  private val core                                     = async.core
  private implicit val ec: ExecutionContext            = async.ec

  import com.couchbase.client.scala.util.DurationConversions._

  def name: String = async.name

  /** Inserts a full document into this collection, if it does not exist already.
    *
    * See [[com.couchbase.client.scala.Collection.insert]] for details.  $Same */
  def insert[T](
      id: String,
      content: T,
      durability: Durability = Disabled,
      timeout: Duration = Duration.MinusInf
  )(implicit serializer: JsonSerializer[T]): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.insert(id, content, durability, timeout)))
  }

  /** Inserts a full document into this collection, if it does not exist already.
    *
    * See [[com.couchbase.client.scala.Collection.insert]] for details.  $Same */
  def insert[T](
      id: String,
      content: T,
      options: InsertOptions
  )(implicit serializer: JsonSerializer[T]): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.insert(id, content, options)))
  }

  /** Replaces the contents of a full document in this collection, if it already exists.
    *
    * See [[com.couchbase.client.scala.Collection.replace]] for details.  $Same */
  def replace[T](
      id: String,
      content: T,
      cas: Long = 0,
      durability: Durability = Disabled,
      timeout: Duration = Duration.MinusInf
  )(implicit serializer: JsonSerializer[T]): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.replace(id, content, cas, durability, timeout)))
  }

  /** Replaces the contents of a full document in this collection, if it already exists.
    *
    * See [[com.couchbase.client.scala.Collection.replace]] for details.  $Same */
  def replace[T](
      id: String,
      content: T,
      options: ReplaceOptions
  )(implicit serializer: JsonSerializer[T]): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.replace(id, content, options)))
  }

  /** Upserts the contents of a full document in this collection.
    *
    * See [[com.couchbase.client.scala.Collection.upsert]] for details.  $Same */
  def upsert[T](
      id: String,
      content: T,
      durability: Durability = Disabled,
      timeout: Duration = Duration.MinusInf
  )(implicit serializer: JsonSerializer[T]): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.upsert(id, content, durability, timeout)))
  }

  /** Upserts the contents of a full document in this collection.
    *
    * See [[com.couchbase.client.scala.Collection.upsert]] for details.  $Same */
  def upsert[T](
      id: String,
      content: T,
      options: UpsertOptions
  )(implicit serializer: JsonSerializer[T]): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.upsert(id, content, options)))
  }

  /** Removes a document from this collection, if it exists.
    *
    * See [[com.couchbase.client.scala.Collection.remove]] for details.  $Same */
  def remove(
      id: String,
      cas: Long = 0,
      durability: Durability = Disabled,
      timeout: Duration = Duration.MinusInf
  ): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.remove(id, cas, durability, timeout)))
  }

  /** Removes a document from this collection, if it exists.
    *
    * See [[com.couchbase.client.scala.Collection.remove]] for details.  $Same */
  def remove(
      id: String,
      options: RemoveOptions
  ): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.remove(id, options)))
  }

  /** Fetches a full document from this collection.
    *
    * See [[com.couchbase.client.scala.Collection.get]] for details.  $Same */
  def get(
      id: String,
      timeout: Duration = kvReadTimeout
  ): SMono[GetResult] = {
    SMono.defer(() => SMono.fromFuture(async.get(id, timeout)))
  }

  /** Fetches a full document from this collection.
    *
    * See [[com.couchbase.client.scala.Collection.get]] for details.  $Same */
  def get(
      id: String,
      options: GetOptions
  ): SMono[GetResult] = {
    SMono.defer(() => SMono.fromFuture(async.get(id, options)))
  }

  /** SubDocument mutations allow modifying parts of a JSON document directly, which can be more efficiently than
    * fetching and modifying the full document.
    *
    * See [[com.couchbase.client.scala.Collection.mutateIn]] for details.  $Same */
  def mutateIn(
      id: String,
      spec: Seq[MutateInSpec],
      cas: Long = 0,
      document: StoreSemantics = StoreSemantics.Replace,
      durability: Durability = Disabled,
      timeout: Duration = Duration.MinusInf
  ): SMono[MutateInResult] = {
    SMono.defer(
      () => SMono.fromFuture(async.mutateIn(id, spec, cas, document, durability, timeout))
    )
  }

  /** SubDocument mutations allow modifying parts of a JSON document directly, which can be more efficiently than
    * fetching and modifying the full document.
    *
    * See [[com.couchbase.client.scala.Collection.mutateIn]] for details.  $Same */
  def mutateIn(
      id: String,
      spec: Seq[MutateInSpec],
      options: MutateInOptions
  ): SMono[MutateInResult] = {
    SMono.defer(() => SMono.fromFuture(async.mutateIn(id, spec, options)))
  }

  /** Fetches a full document from this collection, and simultaneously lock the document from writes.
    *
    * See [[com.couchbase.client.scala.Collection.getAndLock]] for details.  $Same */
  def getAndLock(
      id: String,
      lockTime: Duration,
      timeout: Duration = kvReadTimeout
  ): SMono[GetResult] = {
    SMono.defer(() => SMono.fromFuture(async.getAndLock(id, lockTime, timeout)))
  }

  /** Fetches a full document from this collection, and simultaneously lock the document from writes.
    *
    * See [[com.couchbase.client.scala.Collection.getAndLock]] for details.  $Same */
  def getAndLock(
      id: String,
      lockTime: Duration,
      options: GetAndLockOptions
  ): SMono[GetResult] = {
    SMono.defer(() => SMono.fromFuture(async.getAndLock(id, lockTime, options)))
  }

  /** Unlock a locked document.
    *
    * See [[com.couchbase.client.scala.Collection.unlock]] for details.  $Same */
  def unlock(
      id: String,
      cas: Long,
      timeout: Duration = kvReadTimeout
  ): SMono[Unit] = {
    SMono.defer(() => SMono.fromFuture(async.unlock(id, cas, timeout)))
  }

  /** Unlock a locked document.
    *
    * See [[com.couchbase.client.scala.Collection.unlock]] for details.  $Same */
  def unlock(
      id: String,
      cas: Long,
      options: UnlockOptions
  ): SMono[Unit] = {
    SMono.defer(() => SMono.fromFuture(async.unlock(id, cas, options)))
  }

  /** Fetches a full document from this collection, and simultaneously update the expiry value of the document.
    *
    * See [[com.couchbase.client.scala.Collection.getAndTouch]] for details.  $Same */
  def getAndTouch(
      id: String,
      expiry: Duration,
      timeout: Duration = kvReadTimeout
  ): SMono[GetResult] = {
    SMono.defer(() => SMono.fromFuture(async.getAndTouch(id, expiry, timeout)))
  }

  /** Fetches a full document from this collection, and simultaneously update the expiry value of the document.
    *
    * See [[com.couchbase.client.scala.Collection.getAndTouch]] for details.  $Same */
  def getAndTouch(
      id: String,
      expiry: Duration,
      options: GetAndTouchOptions
  ): SMono[GetResult] = {
    SMono.defer(() => SMono.fromFuture(async.getAndTouch(id, expiry, options)))
  }

  /** SubDocument lookups allow retrieving parts of a JSON document directly, which may be more efficient than
    * retrieving the entire document.
    *
    * See [[com.couchbase.client.scala.Collection.lookupIn]] for details.  $Same */
  def lookupIn(
      id: String,
      spec: Seq[LookupInSpec],
      timeout: Duration = kvReadTimeout
  ): SMono[LookupInResult] = {
    SMono.defer(() => SMono.fromFuture(async.lookupIn(id, spec, timeout)))
  }

  /** SubDocument lookups allow retrieving parts of a JSON document directly, which may be more efficient than
    * retrieving the entire document.
    *
    * See [[com.couchbase.client.scala.Collection.lookupIn]] for details.  $Same */
  def lookupIn(
      id: String,
      spec: Seq[LookupInSpec],
      options: LookupInOptions
  ): SMono[LookupInResult] = {
    SMono.defer(() => SMono.fromFuture(async.lookupIn(id, spec, options)))
  }

  /** Retrieves any available version of the document.
    *
    * See [[com.couchbase.client.scala.Collection.getAnyReplica]] for details.  $Same */
  def getAnyReplica(
      id: String,
      timeout: Duration = kvReadTimeout
  ): SMono[GetReplicaResult] = {
    SMono.defer(() => getAnyReplica(id, GetAnyReplicaOptions().timeout(timeout)))
  }

  /** Retrieves any available version of the document.
    *
    * See [[com.couchbase.client.scala.Collection.getAnyReplica]] for details.  $Same */
  def getAnyReplica(
      id: String,
      options: GetAnyReplicaOptions
  ): SMono[GetReplicaResult] = {
    val timeout = if (options.timeout == Duration.MinusInf) kvReadTimeout else options.timeout
    getAllReplicas(id, options.convert)
      .timeout(timeout)
      .next()
  }

  /** Retrieves all available versions of the document.
    *
    * See [[com.couchbase.client.scala.Collection.getAllReplicas]] for details.  $Same */
  def getAllReplicas(
      id: String,
      timeout: Duration = kvReadTimeout
  ): SFlux[GetReplicaResult] = {
    getAllReplicas(id, GetAllReplicasOptions().timeout(timeout))
  }

  /** Retrieves all available versions of the document.
    *
    * See [[com.couchbase.client.scala.Collection.getAllReplicas]] for details.  $Same */
  def getAllReplicas(
      id: String,
      options: GetAllReplicasOptions
  ): SFlux[GetReplicaResult] = {
    val retryStrategy = options.retryStrategy.getOrElse(environment.retryStrategy)
    val transcoder    = options.transcoder.getOrElse(environment.transcoder)
    val timeout       = if (options.timeout == Duration.MinusInf) kvReadTimeout else options.timeout

    val reqsTry: Try[Seq[GetRequest]] =
      async.getFromReplicaHandler.requestAll(id, timeout, retryStrategy, options.parentSpan)

    reqsTry match {
      case Failure(err) => SFlux.raiseError(err)

      case Success(reqs: Seq[GetRequest]) =>
        SFlux.defer({
          val monos: Seq[SMono[GetReplicaResult]] = reqs.map(request => {
            core.send(request)

            FutureConversions
              .javaCFToScalaMono(request, request.response(), propagateCancellation = true)
              .flatMap(r => {
                val isReplica = request match {
                  case _: GetRequest => false
                  case _             => true
                }
                async.getFromReplicaHandler.response(request, id, r, isReplica, transcoder) match {
                  case Some(getResult) => SMono.just(getResult)
                  case _               => SMono.empty[GetReplicaResult]
                }
              })
              .doOnTerminate(() => request.context().logicallyComplete())
          })

          SFlux.mergeSequential(monos).timeout(timeout)
        })
    }

  }

  /** Updates the expiry of the document with the given id.
    *
    * See [[com.couchbase.client.scala.Collection.touch]] for details.  $Same */
  def touch(
      id: String,
      expiry: Duration,
      timeout: Duration = kvReadTimeout
  ): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.touch(id, expiry, timeout)))
  }

  /** Updates the expiry of the document with the given id.
    *
    * See [[com.couchbase.client.scala.Collection.touch]] for details.  $Same */
  def touch(
      id: String,
      expiry: Duration,
      options: TouchOptions
  ): SMono[MutationResult] = {
    SMono.defer(() => SMono.fromFuture(async.touch(id, expiry, options)))
  }

  /** Checks if a document exists.
    *
    * See [[com.couchbase.client.scala.Collection.exists]] for details.  $Same */
  def exists(
      id: String,
      timeout: Duration = kvReadTimeout
  ): SMono[ExistsResult] = {
    SMono.defer(() => SMono.fromFuture(async.exists(id, timeout)))
  }

  /** Checks if a document exists.
    *
    * See [[com.couchbase.client.scala.Collection.exists]] for details.  $Same */
  def exists(
      id: String,
      options: ExistsOptions
  ): SMono[ExistsResult] = {
    SMono.defer(() => SMono.fromFuture(async.exists(id, options)))
  }
}
