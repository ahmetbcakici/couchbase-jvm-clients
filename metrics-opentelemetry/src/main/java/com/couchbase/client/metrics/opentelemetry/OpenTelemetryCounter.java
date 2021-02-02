/*
 * Copyright (c) 2020 Couchbase, Inc.
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

package com.couchbase.client.metrics.opentelemetry;

import com.couchbase.client.core.cnc.Counter;
import io.opentelemetry.api.metrics.BoundLongCounter;

public class OpenTelemetryCounter implements Counter {

  private final BoundLongCounter counter;
  public OpenTelemetryCounter(BoundLongCounter counter) {
    this.counter = counter;
  }
  @Override
  public void incrementBy(long number) {
    counter.add(number);
  }

}
