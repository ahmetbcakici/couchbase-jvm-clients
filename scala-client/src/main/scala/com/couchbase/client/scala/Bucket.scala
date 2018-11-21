/*
 * Copyright (c) 2018 Couchbase, Inc.
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

import com.couchbase.client.scala.api.QueryOptions
import com.couchbase.client.scala.query.{N1qlQueryResult, N1qlResult}

class Bucket(cluster: Cluster,
             name: String) {
  def openCollection(scope: String, collection: String) = {
    val scope = new Scope(cluster.core, cluster, this, scope)
    scope.openCollection(collection)
  }

  def openScope(name: String) = new Scope(cluster.core, cluster, this, name)

  def query(statement: String, query: QueryOptions = QueryOptions()): N1qlQueryResult = {
    null
  }

  def queryAs[T](statement: String, query: QueryOptions = QueryOptions()): N1qlResult[T] = {
    null
  }
}
