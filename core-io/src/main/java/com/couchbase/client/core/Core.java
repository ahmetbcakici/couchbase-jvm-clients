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

package com.couchbase.client.core;

import com.couchbase.client.core.annotation.Stability;
import com.couchbase.client.core.callbacks.BeforeSendRequestCallback;
import com.couchbase.client.core.cnc.Event;
import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.core.cnc.TracingIdentifiers;
import com.couchbase.client.core.cnc.ValueRecorder;
import com.couchbase.client.core.cnc.events.core.BucketClosedEvent;
import com.couchbase.client.core.cnc.events.core.BucketOpenFailedEvent;
import com.couchbase.client.core.cnc.events.core.BucketOpenInitiatedEvent;
import com.couchbase.client.core.cnc.events.core.BucketOpenedEvent;
import com.couchbase.client.core.cnc.events.core.CoreCreatedEvent;
import com.couchbase.client.core.cnc.events.core.InitGlobalConfigFailedEvent;
import com.couchbase.client.core.cnc.events.core.ReconfigurationCompletedEvent;
import com.couchbase.client.core.cnc.events.core.ReconfigurationErrorDetectedEvent;
import com.couchbase.client.core.cnc.events.core.ReconfigurationIgnoredEvent;
import com.couchbase.client.core.cnc.events.core.ServiceReconfigurationFailedEvent;
import com.couchbase.client.core.cnc.events.core.ShutdownCompletedEvent;
import com.couchbase.client.core.cnc.events.core.ShutdownInitiatedEvent;
import com.couchbase.client.core.config.AlternateAddress;
import com.couchbase.client.core.config.BucketConfig;
import com.couchbase.client.core.config.ClusterConfig;
import com.couchbase.client.core.config.ConfigurationProvider;
import com.couchbase.client.core.config.DefaultConfigurationProvider;
import com.couchbase.client.core.config.GlobalConfig;
import com.couchbase.client.core.diagnostics.EndpointDiagnostics;
import com.couchbase.client.core.endpoint.http.CoreHttpClient;
import com.couchbase.client.core.env.Authenticator;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.error.AlreadyShutdownException;
import com.couchbase.client.core.error.ConfigException;
import com.couchbase.client.core.error.GlobalConfigNotFoundException;
import com.couchbase.client.core.error.InvalidArgumentException;
import com.couchbase.client.core.error.RequestCanceledException;
import com.couchbase.client.core.error.UnsupportedConfigMechanismException;
import com.couchbase.client.core.msg.CancellationReason;
import com.couchbase.client.core.msg.Request;
import com.couchbase.client.core.msg.RequestContext;
import com.couchbase.client.core.msg.RequestTarget;
import com.couchbase.client.core.msg.Response;
import com.couchbase.client.core.node.AnalyticsLocator;
import com.couchbase.client.core.node.KeyValueLocator;
import com.couchbase.client.core.node.Locator;
import com.couchbase.client.core.node.Node;
import com.couchbase.client.core.node.NodeIdentifier;
import com.couchbase.client.core.node.RoundRobinLocator;
import com.couchbase.client.core.node.ViewLocator;
import com.couchbase.client.core.service.ServiceScope;
import com.couchbase.client.core.service.ServiceState;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.core.util.HostAndPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.couchbase.client.core.util.CbCollections.isNullOrEmpty;

/**
 * The main entry point into the core layer.
 *
 * <p>This class has been around behind a facade in the 1.x days, but here it is just a plain
 * simple class that can be instantiated and is used across the upper language bindings.</p>
 *
 * @since 2.0.0
 */
@Stability.Volatile
public class Core {

  /**
   * A reasonably unique instance ID.
   */
  private static final int GLOBAL_ID = new SecureRandom().nextInt();

  /**
   * Counts up core ids for each new instance.
   */
  private static final AtomicInteger CORE_IDS = new AtomicInteger();

  /**
   * Locates the right node for the KV service.
   */
  private static final KeyValueLocator KEY_VALUE_LOCATOR = new KeyValueLocator();

  /**
   * Locates the right node for the manager service.
   */
  private static final RoundRobinLocator MANAGER_LOCATOR =
    new RoundRobinLocator(ServiceType.MANAGER);

  /**
   * Locates the right node for the query service.
   */
  private static final RoundRobinLocator QUERY_LOCATOR =
    new RoundRobinLocator(ServiceType.QUERY);

  /**
   * Locates the right node for the analytics service.
   */
  private static final RoundRobinLocator ANALYTICS_LOCATOR =
    new AnalyticsLocator();

  /**
   * Locates the right node for the search service.
   */
  private static final RoundRobinLocator SEARCH_LOCATOR =
    new RoundRobinLocator(ServiceType.SEARCH);

  /**
   * Locates the right node for the view service.
   */
  private static final RoundRobinLocator VIEWS_LOCATOR =
    new ViewLocator();

  private static final RoundRobinLocator EVENTING_LOCATOR =
    new RoundRobinLocator(ServiceType.EVENTING);

  /**
   * Holds the current core context.
   */
  private final CoreContext coreContext;

  /**
   * Holds the current configuration provider.
   */
  private final ConfigurationProvider configurationProvider;

  /**
   * Holds the current configuration for all buckets.
   */
  private volatile ClusterConfig currentConfig;

  /**
   * The list of currently managed nodes against the cluster.
   */
  private final CopyOnWriteArrayList<Node> nodes;

  /**
   * If a reconfiguration is in process, this will be set to true and prevent concurrent reconfig attempts.
   */
  private final AtomicBoolean reconfigureInProgress = new AtomicBoolean(false);

  /**
   * We use a barrier to only run one reconfiguration at the same time, so when a new one comes in
   * and is ignored we need to set a flag to come back to it once the others finished.
   */
  private final AtomicBoolean moreConfigsPending = new AtomicBoolean(false);

  /**
   * Once shutdown, this will be set to true and as a result no further ops are allowed to go through.
   */
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  /**
   * Reference to the event bus on the environment.
   */
  private final EventBus eventBus;

  /**
   * Holds a reference to the timer used for timeout registration.
   */
  private final Timer timer;

  private final Set<SeedNode> seedNodes;

  private final List<BeforeSendRequestCallback> beforeSendRequestCallbacks;

  /**
   * Holds the response metrics per
   */
  private final Map<ResponseMetricIdentifier, ValueRecorder> responseMetrics = new ConcurrentHashMap<>();

  /**
   * Creates a new {@link Core} with the given environment.
   *
   * @param environment the environment for this core.
   * @return the created {@link Core}.
   */
  public static Core create(final CoreEnvironment environment, final Authenticator authenticator, final Set<SeedNode> seedNodes) {
    return new Core(environment, authenticator, seedNodes);
  }

  /**
   * Creates a new Core.
   *
   * @param environment the environment for this core.
   */
  protected Core(final CoreEnvironment environment, final Authenticator authenticator, final Set<SeedNode> seedNodes) {
    if (environment.securityConfig().tlsEnabled() && !authenticator.supportsTls()) {
      throw new InvalidArgumentException("TLS enabled but the Authenticator does not support TLS!", null, null);
    } else if (!environment.securityConfig().tlsEnabled() && !authenticator.supportsNonTls()) {
      throw new InvalidArgumentException("TLS not enabled but the Authenticator does only support TLS!", null, null);
    }

    this.seedNodes = seedNodes;
    this.coreContext = new CoreContext(this, createInstanceId(), environment, authenticator);
    this.configurationProvider = createConfigurationProvider();
    this.nodes = new CopyOnWriteArrayList<>();
    this.eventBus = environment.eventBus();
    this.timer = environment.timer();
    this.currentConfig = configurationProvider.config();
    this.configurationProvider.configs().subscribe(c -> {
      currentConfig = c;
      reconfigure();
    });
    this.beforeSendRequestCallbacks = environment
      .requestCallbacks()
      .stream()
      .filter(c -> c instanceof BeforeSendRequestCallback)
      .map(c -> (BeforeSendRequestCallback) c)
      .collect(Collectors.toList());

    eventBus.publish(new CoreCreatedEvent(coreContext, environment, seedNodes));
  }

  /**
   * Creates a (somewhat) globally unique ID for this instance.
   * <p>
   * The 64 bit long is split up into an upper and lower 32 bit halves. The upper half
   * is reusing the same global ID for all instances while the lower half is always
   * incrementing for each instance. So it has a global and a local component which can
   * be used to correlate instances across logs but also help distinguish multiple
   * instances in the same JVM.
   *
   * @return the created instance ID.
   */
  private long createInstanceId() {
    return (((long) GLOBAL_ID) << 32) | (CORE_IDS.incrementAndGet() & 0xffffffffL);
  }

  /**
   * During testing this can be overridden so that a custom configuration provider is used
   * in the system.
   *
   * @return by default returns the default config provider.
   */
  ConfigurationProvider createConfigurationProvider() {
    return new DefaultConfigurationProvider(this, seedNodes);
  }

  /**
   * Returns the attached configuration provider.
   *
   * <p>Internal API, use with care!</p>
   */
  @Stability.Internal
  public ConfigurationProvider configurationProvider() {
    return configurationProvider;
  }

  /**
   * Sends a command into the core layer and registers the request with the timeout timer.
   *
   * @param request the request to dispatch.
   */
  public <R extends Response> void send(final Request<R> request) {
    send(request, true);
  }

  /**
   * Sends a command into the core layer and allows to avoid timeout registration.
   *
   * <p>Usually you want to use {@link #send(Request)} instead, this method should only be used during
   * retry situations where the request has already been registered with a timeout timer before.</p>
   *
   * @param request the request to dispatch.
   * @param registerForTimeout if the request should be registered with a timeout.
   */
  @Stability.Internal
  @SuppressWarnings({"unchecked"})
  public <R extends Response> void send(final Request<R> request, final boolean registerForTimeout) {
    if (shutdown.get()) {
      request.cancel(CancellationReason.SHUTDOWN);
      return;
    }

    if (registerForTimeout) {
      timer.register((Request<Response>) request);
      for (BeforeSendRequestCallback cb : beforeSendRequestCallbacks) {
        cb.beforeSend(request);
      }
    }

    locator(request.serviceType()).dispatch(request, nodes, currentConfig, context());
  }

  /**
   * Returns the {@link CoreContext} of this core instance.
   */
  public CoreContext context() {
    return coreContext;
  }

  /**
   * Returns a client for issuing HTTP requests to servers in the cluster.
   */
  @Stability.Internal
  public CoreHttpClient httpClient(RequestTarget target) {
    return new CoreHttpClient(this, target);
  }

  @Stability.Internal
  public Stream<EndpointDiagnostics> diagnostics() {
    return nodes.stream().flatMap(Node::diagnostics);
  }

  /**
   * If present, returns a flux that allows to monitor the state changes of a specific service.
   *
   * @param nodeIdentifier the node identifier for the node.
   * @param type the type of service.
   * @param bucket the bucket, if present.
   * @return if found, a flux with the service states.
   */
  @Stability.Internal
  public Optional<Flux<ServiceState>> serviceState(NodeIdentifier nodeIdentifier, ServiceType type, Optional<String> bucket) {
    for (Node node : nodes) {
      if (node.identifier().equals(nodeIdentifier)) {
        return node.serviceState(type, bucket);
      }
    }
    return Optional.empty();
  }

  /**
   * Instructs the client to, if possible, load and initialize the global config.
   *
   * <p>Since global configs are an "optional" feature depending on the cluster version, if an error happens
   * this method will not fail. Rather it will log the exception (with some logic dependent on the type of error)
   * and will allow the higher level components to move on where possible.</p>
   */
  @Stability.Internal
  public void initGlobalConfig() {
    long start = System.nanoTime();
    configurationProvider
      .loadAndRefreshGlobalConfig()
      .subscribe(
        v -> {},
        throwable -> {
          InitGlobalConfigFailedEvent.Reason reason = InitGlobalConfigFailedEvent.Reason.UNKNOWN;
          if (throwable instanceof UnsupportedConfigMechanismException) {
            reason = InitGlobalConfigFailedEvent.Reason.UNSUPPORTED;
          } else if (throwable instanceof GlobalConfigNotFoundException) {
            reason = InitGlobalConfigFailedEvent.Reason.NO_CONFIG_FOUND;
          } else if (throwable instanceof ConfigException) {
            if (throwable.getCause() instanceof RequestCanceledException) {
              RequestContext ctx = ((RequestCanceledException) throwable.getCause()).context().requestContext();
              if (ctx.request().cancellationReason() == CancellationReason.SHUTDOWN) {
                reason = InitGlobalConfigFailedEvent.Reason.SHUTDOWN;
              }
            } else if (throwable.getMessage().contains("NO_ACCESS")) {
              reason = InitGlobalConfigFailedEvent.Reason.NO_ACCESS;
            }
          } else if (throwable instanceof AlreadyShutdownException) {
            reason = InitGlobalConfigFailedEvent.Reason.SHUTDOWN;
          }
          eventBus.publish(new InitGlobalConfigFailedEvent(
            reason.severity(),
            Duration.ofNanos(System.nanoTime() - start),
            context(),
            reason,
            throwable
          ));
        }
      );
  }

  /**
   * Attempts to open a bucket and fails the {@link Mono} if there is a persistent error
   * as the reason.
   */
  @Stability.Internal
  public void openBucket(final String name) {
    eventBus.publish(new BucketOpenInitiatedEvent(coreContext, name));

    long start = System.nanoTime();
    configurationProvider
      .openBucket(name)
      .subscribe(
        v -> {},
        t -> {
          Event.Severity severity = t instanceof AlreadyShutdownException
            ? Event.Severity.DEBUG
            : Event.Severity.WARN;
          eventBus.publish(new BucketOpenFailedEvent(
            name,
            severity,
            Duration.ofNanos(System.nanoTime() - start),
            coreContext,
            t
          ));
        },
        () -> eventBus.publish(new BucketOpenedEvent(
          Duration.ofNanos(System.nanoTime() - start),
          coreContext,
          name
        )));
  }

  /**
   * This API provides access to the current config that is published throughout the core.
   *
   * <p>Note that this is internal API and might change at any time.</p>
   */
  @Stability.Internal
  public ClusterConfig clusterConfig() {
    return configurationProvider.config();
  }

  /**
   * Attempts to close a bucket and fails the {@link Mono} if there is a persistent error
   * as the reason.
   */
  private Mono<Void> closeBucket(final String name) {
    return Mono.defer(() -> {
      long start = System.nanoTime();
      return configurationProvider
        .closeBucket(name)
        .doOnSuccess(ignored -> eventBus.publish(new BucketClosedEvent(
          Duration.ofNanos(System.nanoTime() - start),
          coreContext,
          name
        )));
    });
  }

  /**
   * This method can be used by a caller to make sure a certain service is enabled at the given
   * target node.
   *
   * <p>This is advanced, internal functionality and should only be used if the caller knows
   * what they are doing.</p>
   *
   * @param identifier the node to check.
   * @param serviceType the service type to enable if not enabled already.
   * @param port the port where the service is listening on.
   * @param bucket if the service is bound to a bucket, it needs to be provided.
   * @param alternateAddress if an alternate address is present, needs to be provided since it is passed down
   *                         to the node and its services.
   * @return a {@link Mono} which completes once initiated.
   */
  @Stability.Internal
  public Mono<Void> ensureServiceAt(final NodeIdentifier identifier, final ServiceType serviceType, final int port,
                                    final Optional<String> bucket, final Optional<String> alternateAddress) {
    if (shutdown.get()) {
      // We don't want do add a node if we are already shutdown!
      return Mono.empty();
    }

    return Flux
      .fromIterable(nodes)
      .filter(n -> n.identifier().equals(identifier))
      .switchIfEmpty(Mono.defer(() -> {
        Node node = createNode(identifier, alternateAddress);
        nodes.add(node);
        return Mono.just(node);
      }))
      .flatMap(node -> node.addService(serviceType, port, bucket))
      .then();
  }

  @Stability.Internal
  public ValueRecorder responseMetric(final Request<?> request) {
    return responseMetrics.computeIfAbsent(new ResponseMetricIdentifier(request), key -> {
      Map<String, String> tags = new HashMap<>(4);
      tags.put(TracingIdentifiers.ATTR_SERVICE, key.serviceType.ident());
      tags.put(TracingIdentifiers.ATTR_OPERATION, key.requestName);
      return coreContext.environment().meter().valueRecorder(TracingIdentifiers.METER_OPERATIONS, tags);
    });
  }


  /**
   * Create a {@link Node} from the given identifier.
   *
   * <p>This method is here so it can be overridden in tests.</p>
   *
   * @param identifier the identifier for the node.
   * @param alternateAddress the alternate address if present.
   * @return the created node instance.
   */
  protected Node createNode(final NodeIdentifier identifier, final Optional<String> alternateAddress) {
    return Node.create(coreContext, identifier, alternateAddress);
  }

  /**
   * Check if the given {@link Node} needs to be removed from the cluster topology.
   *
   * @param node the node in question
   * @param config the current config.
   * @return a mono once disconnected (or completes immediately if there is no need to do so).
   */
  private Mono<Void> maybeRemoveNode(final Node node, final ClusterConfig config) {
    return Mono.defer(() -> {
      boolean stillPresentInBuckets = config
        .bucketConfigs()
        .values()
        .stream()
        .flatMap(bc -> bc.nodes().stream())
        .anyMatch(ni -> ni.identifier().equals(node.identifier()));


      boolean stillPresentInGlobal;
      if (config.globalConfig() != null) {
        stillPresentInGlobal = config
          .globalConfig()
          .portInfos()
          .stream()
          .anyMatch(ni -> ni.identifier().equals(node.identifier()));
      } else {
        stillPresentInGlobal = false;
      }

      if ((!stillPresentInBuckets && !stillPresentInGlobal) || !node.hasServicesEnabled()) {
        return node.disconnect().doOnTerminate(() -> nodes.remove(node));
      }

      return Mono.empty();
    });
  }

  /**
   * This method is used to remove a service from a node.
   *
   * @param identifier the node to check.
   * @param serviceType the service type to remove if present.
   * @return a {@link Mono} which completes once initiated.
   */
  private Mono<Void> removeServiceFrom(final NodeIdentifier identifier, final ServiceType serviceType,
                                       final Optional<String> bucket) {
    return Flux
      .fromIterable(new ArrayList<>(nodes))
      .filter(n -> n.identifier().equals(identifier))
      .filter(node -> node.serviceEnabled(serviceType))
      .flatMap(node -> node.removeService(serviceType, bucket))
      .then();
  }

  @Stability.Internal
  public Mono<Void> shutdown() {
    return shutdown(coreContext.environment().timeoutConfig().disconnectTimeout());
  }

  /**
   * Shuts down this core and all associated, owned resources.
   */
  @Stability.Internal
  public Mono<Void> shutdown(Duration timeout) {
    return Mono.defer(() -> {
      long start = System.nanoTime();
      if (shutdown.compareAndSet(false, true)) {
        eventBus.publish(new ShutdownInitiatedEvent(coreContext));

        return Flux
          .fromIterable(currentConfig.bucketConfigs().keySet())
          .flatMap(this::closeBucket)
          .then(configurationProvider.shutdown())
          // every 10ms check if all nodes have been cleared, and then move on.
          // this links the config provider shutdown with our core reconfig logic
          .then(Flux.interval(Duration.ofMillis(10), coreContext.environment().scheduler()).takeUntil(i -> nodes.isEmpty()).then())
          .doOnTerminate(() -> eventBus.publish(
            new ShutdownCompletedEvent(Duration.ofNanos(System.nanoTime() - start), coreContext)
          ))
          .then();
      }
      return Mono.empty();
    }).timeout(timeout, coreContext.environment().scheduler());
  }

  /**
   * Reconfigures the SDK topology to align with the current server configuration.
   *
   * <p>When reconfigure is called, it will grab a current configuration and then add/remove
   * nodes/services to mirror the current topology and configuration settings.</p>
   *
   * <p>This is a eventually consistent process, so in-flight operations might still be rescheduled
   * and then picked up later (or cancelled, depending on the strategy). For those coming from 1.x,
   * it works very similar.</p>
   */
  private void reconfigure() {
    if (reconfigureInProgress.compareAndSet(false, true)) {
      final ClusterConfig configForThisAttempt = currentConfig;

      if (configForThisAttempt.bucketConfigs().isEmpty() && configForThisAttempt.globalConfig() == null) {
        reconfigureDisconnectAll();
        return;
      }

      final long start = System.nanoTime();
      Flux<BucketConfig> bucketConfigFlux = Flux
        .just(configForThisAttempt)
        .flatMap(cc -> Flux.fromIterable(cc.bucketConfigs().values()));

      reconfigureBuckets(bucketConfigFlux)
        .then(reconfigureGlobal(configForThisAttempt.globalConfig()))
        .then(Mono.defer(() ->
          Flux
            .fromIterable(new ArrayList<>(nodes))
            .flatMap(n -> maybeRemoveNode(n, configForThisAttempt))
            .then()
        ))
        .subscribe(
        v -> {},
        e -> {
          clearReconfigureInProgress();
          eventBus.publish(new ReconfigurationErrorDetectedEvent(context(), e));
        },
        () -> {
          clearReconfigureInProgress();
          eventBus.publish(new ReconfigurationCompletedEvent(
            Duration.ofNanos(System.nanoTime() - start),
            coreContext
          ));
        }
      );
    } else {
      moreConfigsPending.set(true);
      eventBus.publish(new ReconfigurationIgnoredEvent(coreContext));
    }
  }

  /**
   * This reconfiguration sequence takes all nodes and disconnects them.
   *
   * <p>This is usually called by the parent {@link #reconfigure()} when all buckets are closed which
   * points to a shutdown/all buckets closed disconnect phase.</p>
   */
  private void reconfigureDisconnectAll() {
    long start = System.nanoTime();
    Flux
      .fromIterable(new ArrayList<>(nodes))
      .flatMap(Node::disconnect)
      .doOnComplete(nodes::clear)
      .subscribe(
        v -> {},
        e -> {
          clearReconfigureInProgress();
          eventBus.publish(new ReconfigurationErrorDetectedEvent(context(), e));
        },
        () -> {
          clearReconfigureInProgress();
          eventBus.publish(new ReconfigurationCompletedEvent(
            Duration.ofNanos(System.nanoTime() - start),
            coreContext
          ));
        }
      );
  }

  /**
   * Clean reconfiguration in progress and check if there is a new one we need to try.
   */
  private void clearReconfigureInProgress() {
    reconfigureInProgress.set(false);
    if (moreConfigsPending.compareAndSet(true, false)) {
      reconfigure();
    }
  }

  private Mono<Void> reconfigureGlobal(final GlobalConfig config) {
    return Mono.defer(() -> {
      if (config == null) {
        return Mono.empty();
      }

      return Flux
        .fromIterable(config.portInfos())
        .flatMap(ni -> {
          boolean tls = coreContext.environment().securityConfig().tlsEnabled();

          Set<Map.Entry<ServiceType, Integer>> aServices = null;
          Optional<String> alternateAddress = coreContext.alternateAddress();
          String aHost = null;
          if (alternateAddress.isPresent()) {
            AlternateAddress aa = ni.alternateAddresses().get(alternateAddress.get());
            aHost = aa.hostname();
            aServices = tls ? aa.sslServices().entrySet() : aa.services().entrySet();
          }

          if (aServices == null || aServices.isEmpty()) {
            aServices = tls ? ni.sslPorts().entrySet() : ni.ports().entrySet();
          }

          final String alternateHost = aHost;
          final Set<Map.Entry<ServiceType, Integer>> services = aServices;

          Flux<Void> serviceRemoveFlux = Flux
            .fromIterable(Arrays.asList(ServiceType.values()))
            .filter(s -> {
              for (Map.Entry<ServiceType, Integer> inConfig : services) {
                if (inConfig.getKey() == s) {
                  return false;
                }
              }
              return true;
            })
            .flatMap(s -> removeServiceFrom(
              ni.identifier(),
              s,
              Optional.empty())
              .onErrorResume(throwable -> {
                eventBus.publish(new ServiceReconfigurationFailedEvent(
                  coreContext,
                  ni.hostname(),
                  s,
                  throwable
                ));
                return Mono.empty();
              })
            );


          Flux<Void> serviceAddFlux = Flux
            .fromIterable(services)
            .flatMap(s -> ensureServiceAt(
              ni.identifier(),
              s.getKey(),
              s.getValue(),
              Optional.empty(),
              Optional.ofNullable(alternateHost))
              .onErrorResume(throwable -> {
                eventBus.publish(new ServiceReconfigurationFailedEvent(
                  coreContext,
                  ni.hostname(),
                  s.getKey(),
                  throwable
                ));
                return Mono.empty();
              })
            );

          return Flux.merge(serviceAddFlux, serviceRemoveFlux);
        })
        .then();
    });
  }

  /**
   * Contains logic to perform reconfiguration for a bucket config.
   *
   * @param bucketConfigs the flux of bucket configs currently open.
   * @return a mono once reconfiguration for all buckets is complete
   */
  private Mono<Void> reconfigureBuckets(final Flux<BucketConfig> bucketConfigs) {
    return bucketConfigs.flatMap(bc ->
      Flux.fromIterable(bc.nodes())
        .flatMap(ni -> {
          boolean tls = coreContext.environment().securityConfig().tlsEnabled();

          Set<Map.Entry<ServiceType, Integer>> aServices = null;
          Optional<String> alternateAddress = coreContext.alternateAddress();
          String aHost = null;
          if (alternateAddress.isPresent()) {
            AlternateAddress aa = ni.alternateAddresses().get(alternateAddress.get());
            aHost = aa.hostname();
            aServices = tls ? aa.sslServices().entrySet() : aa.services().entrySet();
          }


          if (isNullOrEmpty(aServices)) {
            aServices = tls ? ni.sslServices().entrySet() : ni.services().entrySet();
          }

          final String alternateHost = aHost;
          final Set<Map.Entry<ServiceType, Integer>> services = aServices;

          Flux<Void> serviceRemoveFlux = Flux
            .fromIterable(Arrays.asList(ServiceType.values()))
            .filter(s -> {
              for (Map.Entry<ServiceType, Integer> inConfig : services) {
                if (inConfig.getKey() == s) {
                  return false;
                }
              }
              return true;
            })
            .flatMap(s -> removeServiceFrom(
              ni.identifier(),
              s,
              s.scope() == ServiceScope.BUCKET ? Optional.of(bc.name()) : Optional.empty())
              .onErrorResume(throwable -> {
                eventBus.publish(new ServiceReconfigurationFailedEvent(
                  coreContext,
                  ni.hostname(),
                  s,
                  throwable
                ));
                return Mono.empty();
              })
            );

          Flux<Void> serviceAddFlux = Flux
            .fromIterable(services)
            .flatMap(s -> ensureServiceAt(
              ni.identifier(),
              s.getKey(),
              s.getValue(),
              s.getKey().scope() == ServiceScope.BUCKET ? Optional.of(bc.name()) : Optional.empty(),
              Optional.ofNullable(alternateHost))
              .onErrorResume(throwable -> {
                eventBus.publish(new ServiceReconfigurationFailedEvent(
                  coreContext,
                  ni.hostname(),
                  s.getKey(),
                  throwable
                ));
                return Mono.empty();
              })
            );

          return Flux.merge(serviceAddFlux, serviceRemoveFlux);
        })
    ).then();
  }

  /**
   * Helper method to match the right locator to the given service type.
   *
   * @param serviceType the service type for which a locator should be returned.
   * @return the locator for the service type, or an exception if unknown.
   */
  private static Locator locator(final ServiceType serviceType) {
    switch (serviceType) {
      case KV:
        return KEY_VALUE_LOCATOR;
      case MANAGER:
        return MANAGER_LOCATOR;
      case QUERY:
        return QUERY_LOCATOR;
      case ANALYTICS:
        return ANALYTICS_LOCATOR;
      case SEARCH:
        return SEARCH_LOCATOR;
      case VIEWS:
        return VIEWS_LOCATOR;
      case EVENTING:
        return EVENTING_LOCATOR;
      default:
        throw new IllegalStateException("Unsupported ServiceType: " + serviceType);
    }
  }

  private static class ResponseMetricIdentifier {

    private final ServiceType serviceType;
    private final HostAndPort lastDispatchedTo;
    private final String requestName;

    ResponseMetricIdentifier(final Request<?> request) {
      this.serviceType = request.serviceType();
      this.lastDispatchedTo = request.context().lastDispatchedTo();
      this.requestName = request.name();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ResponseMetricIdentifier that = (ResponseMetricIdentifier) o;
      return serviceType == that.serviceType &&
        Objects.equals(lastDispatchedTo, that.lastDispatchedTo) &&
        Objects.equals(requestName, that.requestName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(serviceType, lastDispatchedTo, requestName);
    }
  }

}
