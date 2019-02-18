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

package com.couchbase.client.java;

import com.couchbase.client.core.env.Credentials;
import com.couchbase.client.core.env.OwnedSupplier;
import com.couchbase.client.java.analytics.AnalyticsOptions;
import com.couchbase.client.java.analytics.AnalyticsResult;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.search.SearchOptions;
import com.couchbase.client.java.search.SearchQuery;
import com.couchbase.client.java.search.SearchResult;

import java.util.function.Supplier;

import static com.couchbase.client.java.AsyncUtils.block;

public class Cluster {

  private final AsyncCluster asyncCluster;
  private final ReactiveCluster reactiveCluster;

  public static Cluster connect(final String username, final String password) {
    return new Cluster(new OwnedSupplier<>(ClusterEnvironment.create(username, password)));
  }

  public static Cluster connect(final Credentials credentials) {
    return new Cluster(new OwnedSupplier<>(ClusterEnvironment.create(credentials)));
  }

  public static Cluster connect(final String connectionString, final String username, final String password) {
    return new Cluster(new OwnedSupplier<>(ClusterEnvironment.create(connectionString, username, password)));
  }

  public static Cluster connect(final String connectionString, final Credentials credentials) {
    return new Cluster(new OwnedSupplier<>(ClusterEnvironment.create(connectionString, credentials)));
  }

  public static Cluster connect(final ClusterEnvironment environment) {
    return new Cluster(() -> environment);
  }

  private Cluster(final Supplier<ClusterEnvironment> environment) {
    this.asyncCluster = new AsyncCluster(environment);
    this.reactiveCluster = new ReactiveCluster(asyncCluster);
  }

  public AsyncCluster async() {
    return asyncCluster;
  }

  public ReactiveCluster reactive() {
    return reactiveCluster;
  }

  public QueryResult query(final String statement) {
    return new QueryResult(block(async().query(statement, null)));
  }

  public QueryResult query(final String statement, final QueryOptions options) {
    return new QueryResult(block(async().query(statement, null, options)));
  }

  /*
  public AnalyticsResult analyticsQuery(final String statement) {
    return new AnalyticsResult(block(async().analyticsQuery(statement)));
  }

  public AnalyticsResult analyticsQuery(final String statement, final AnalyticsOptions options) {
    return new AnalyticsResult(block(async().analyticsQuery(statement, options)));
  }

  public SearchResult searchQuery(final SearchQuery query) {
    return new SearchResult(block(async().searchQuery(query)));
  }

  public SearchResult searchQuery(final SearchQuery query, final SearchOptions options) {
    return new SearchResult(block(async().searchQuery(query, options)));
  }*/

  public Bucket bucket(String name) {
    AsyncBucket b = block(asyncCluster.bucket(name));
    return new Bucket(b);
  }


  public void shutdown() {
    block(asyncCluster.shutdown());
  }

}
