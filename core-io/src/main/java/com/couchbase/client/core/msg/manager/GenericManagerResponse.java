/*
 * Copyright 2019 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.client.core.msg.manager;

import com.couchbase.client.core.msg.BaseResponse;
import com.couchbase.client.core.msg.ResponseStatus;

import java.nio.charset.StandardCharsets;

import static com.couchbase.client.core.logging.RedactableArgument.redactMeta;
import static java.util.Objects.requireNonNull;

public class GenericManagerResponse extends BaseResponse {

  private final int httpStatus;
  private final byte[] content;

  public GenericManagerResponse(ResponseStatus status, byte[] content, int httpStatus) {
    super(status);
    this.httpStatus = httpStatus;
    this.content = requireNonNull(content);
  }

  public int httpStatus() {
    return httpStatus;
  }

  public byte[] content() {
    return content;
  }

  @Override
  public String toString() {
    return "GenericManagerResponse{" +
      "status=" + status() +
      ", httpStatus=" + httpStatus +
      ", content=" + redactMeta(new String(content, StandardCharsets.UTF_8)) +
      '}';
  }
}
