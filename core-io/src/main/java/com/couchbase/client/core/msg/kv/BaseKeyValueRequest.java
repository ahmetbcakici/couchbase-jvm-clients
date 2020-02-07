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

package com.couchbase.client.core.msg.kv;

import com.couchbase.client.core.CoreContext;
import com.couchbase.client.core.cnc.InternalSpan;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBufAllocator;
import com.couchbase.client.core.error.CollectionNotFoundException;
import com.couchbase.client.core.error.FeatureNotAvailableException;
import com.couchbase.client.core.error.InvalidArgumentException;
import com.couchbase.client.core.error.context.ReducedKeyValueErrorContext;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.core.io.netty.kv.KeyValueChannelContext;
import com.couchbase.client.core.msg.BaseRequest;
import com.couchbase.client.core.msg.Response;
import com.couchbase.client.core.retry.RetryStrategy;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.core.util.Bytes;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.couchbase.client.core.logging.RedactableArgument.redactMeta;
import static com.couchbase.client.core.logging.RedactableArgument.redactUser;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The {@link BaseKeyValueRequest} should be subclassed by all KeyValue requests since it
 * provides common ground for all of them (i.e. adding the kv partition needed).
 *
 * @since 2.0.0
 */
public abstract class BaseKeyValueRequest<R extends Response>
  extends BaseRequest<R>
  implements KeyValueRequest<R> {

  /**
   * The opaque identifier used in the binary protocol to track requests/responses.
   *
   * No overflow control is applied, since once it overflows it starts with negative values again.
   */
  private static final AtomicInteger GLOBAL_OPAQUE = new AtomicInteger(0);

  /**
   * The maximum length of the key in bytes, also include the collection info if needed.
   */
  private static final int MAX_KEY_BYTES = 250;

  /**
   * The key without the collection associated.
   */
  private final byte[] key;

  /**
   * The collection identifier tuple.
   */
  private final CollectionIdentifier collectionIdentifier;

  /**
   * Holds the opaque for this request.
   */
  private final int opaque;

  /**
   * Once set, stores the partition where this request should be dispatched against.
   */
  private volatile short partition;

  protected BaseKeyValueRequest(final Duration timeout, final CoreContext ctx, final RetryStrategy retryStrategy,
                                final String key, final CollectionIdentifier collectionIdentifier) {
    this(timeout, ctx, retryStrategy, key, collectionIdentifier, null);
  }

  protected BaseKeyValueRequest(final Duration timeout, final CoreContext ctx, final RetryStrategy retryStrategy,
                                final String key, final CollectionIdentifier collectionIdentifier, final InternalSpan span) {
    super(timeout, ctx, retryStrategy, span);
    this.key = encodeKey(key);
    this.collectionIdentifier = collectionIdentifier;
    this.opaque = nextOpaque();
  }

  public static int nextOpaque() {
    return GLOBAL_OPAQUE.getAndIncrement();
  }

  @Override
  public short partition() {
    return partition;
  }

  @Override
  public void partition(short partition) {
    this.partition = partition;
  }

  /**
   * Returns the encoded version of the key in UTF-8.
   *
   * @param key the key to encode.
   * @return the encoded key.
   */
  static byte[] encodeKey(final String key) {
    return key == null || key.isEmpty() ? Bytes.EMPTY_BYTE_ARRAY : key.getBytes(UTF_8);
  }

  /**
   * This method with return an encoded key with or without the collection prefix, depending on the
   * context provided.
   *
   * @param alloc the buffer allocator to use.
   * @param ctx the channel context.
   * @return the encoded ID, maybe with the collection prefix in place.
   */
  protected ByteBuf encodedKeyWithCollection(final ByteBufAllocator alloc, final KeyValueChannelContext ctx) {
    if (ctx.collectionsEnabled()) {
      byte[] collection = ctx.collectionMap().get(collectionIdentifier);
      if (collection == null) {
        throw CollectionNotFoundException.forCollection(collectionIdentifier.collection().orElse(""));
      }

      int totalLength = key.length + collection.length;
      checkKeyLength(totalLength);
      return alloc
        .buffer(totalLength)
        .writeBytes(collection)
        .writeBytes(key);
    } else {
      if (collectionIdentifier.isDefault()) {
        checkKeyLength(key.length);
        return alloc.buffer(key.length).writeBytes(key);
      } else {
        throw new FeatureNotAvailableException("Collections are not supported (or enabled) on the cluster");
      }
    }
  }

  /**
   * Checks the key length and throws if too long.
   *
   * @param length the length of the key.
   */
  private void checkKeyLength(final int length) {
    if (length > MAX_KEY_BYTES) {
      throw new InvalidArgumentException(
        "The key must not be longer than " + MAX_KEY_BYTES + " bytes (was " + length + " bytes including the collection prefix).",
        null,
        ReducedKeyValueErrorContext.create(new String(key, UTF_8), collectionIdentifier)
      );
    }
  }

  @Override
  public ServiceType serviceType() {
    return ServiceType.KV;
  }

  public int opaque() {
    return opaque;
  }

  @Override
  public Map<String, Object> serviceContext() {
    Map<String, Object> ctx = new TreeMap<>();
    ctx.put("type", serviceType().ident());
    ctx.put("opaque", operationId());

    if (collectionIdentifier != null) {
      ctx.put("bucket", redactMeta(collectionIdentifier.bucket()));
      ctx.put("scope", redactMeta(collectionIdentifier.scope().orElse(CollectionIdentifier.DEFAULT_SCOPE)));
      ctx.put("collection", redactMeta(collectionIdentifier.collection().orElse(CollectionIdentifier.DEFAULT_COLLECTION)));
    }

    if (key != null && key.length > 0) {
      ctx.put("documentId", redactUser(new String(key, UTF_8)));
    }

    if (this instanceof SyncDurabilityRequest) {
      ctx.put("syncDurability", ((SyncDurabilityRequest) this).durabilityLevel());
    }

    return ctx;
  }

  @Override
  public byte[] key() {
    return key;
  }

  @Override
  public String bucket() {
    return collectionIdentifier == null ? null : collectionIdentifier.bucket();
  }

  @Override
  public CollectionIdentifier collectionIdentifier() {
    return collectionIdentifier;
  }

  @Override
  public String operationId() {
    return "0x" + Integer.toHexString(opaque);
  }

}
