// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.functions.invoker;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.CloudEventsFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageReader;
import io.cloudevents.http.HttpMessageFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's background function. */
public final class BackgroundFunctionExecutor extends HttpServlet {
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final FunctionExecutor<?> functionExecutor;

  private BackgroundFunctionExecutor(FunctionExecutor<?> functionExecutor) {
    this.functionExecutor = functionExecutor;
  }

  private enum FunctionKind {
    BACKGROUND(BackgroundFunction.class),
    RAW_BACKGROUND(RawBackgroundFunction.class),
    CLOUD_EVENTS(CloudEventsFunction.class);

    static final List<FunctionKind> VALUES = Arrays.asList(values());

    final Class<?> functionClass;

    FunctionKind(Class<?> functionClass) {
      this.functionClass = functionClass;
    }

    /** Returns the {@link FunctionKind} that the given class implements, if any. */
    static Optional<FunctionKind> forClass(Class<?> functionClass) {
      return VALUES.stream()
          .filter(v -> v.functionClass.isAssignableFrom(functionClass))
          .findFirst();
    }
  }

  /**
   * Optionally makes a {@link BackgroundFunctionExecutor} for the given class, if it implements one
   * of {@link BackgroundFunction}, {@link RawBackgroundFunction}, or {@link CloudEventsFunction}.
   * Otherwise returns {@link Optional#empty()}.
   *
   * @param functionClass the class of a possible background function implementation.
   * @throws RuntimeException if the given class does implement one of the required interfaces, but
   *     we are unable to construct an instance using its no-arg constructor.
   */
  public static Optional<BackgroundFunctionExecutor> maybeForClass(Class<?> functionClass) {
    Optional<FunctionKind> maybeFunctionKind = FunctionKind.forClass(functionClass);
    if (!maybeFunctionKind.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(forClass(functionClass, maybeFunctionKind.get()));
  }

  /**
   * Makes a {@link BackgroundFunctionExecutor} for the given class.
   *
   * @throws RuntimeException if either the class does not implement one of {@link
   *     BackgroundFunction}, {@link RawBackgroundFunction}, or {@link CloudEventsFunction}; or we
   *     are unable to construct an instance using its no-arg constructor.
   */
  public static BackgroundFunctionExecutor forClass(Class<?> functionClass) {
    Optional<FunctionKind> maybeFunctionKind = FunctionKind.forClass(functionClass);
    if (!maybeFunctionKind.isPresent()) {
      List<String> classNames =
          FunctionKind.VALUES.stream().map(v -> v.functionClass.getName()).collect(toList());
      throw new RuntimeException(
          "Class "
              + functionClass.getName()
              + " must implement one of these interfaces: "
              + String.join(", ", classNames));
    }
    return forClass(functionClass, maybeFunctionKind.get());
  }

  private static BackgroundFunctionExecutor forClass(
      Class<?> functionClass, FunctionKind functionKind) {
    Object instance;
    try {
      instance = functionClass.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Could not construct an instance of " + functionClass.getName() + ": " + e, e);
    }
    FunctionExecutor<?> executor;
    switch (functionKind) {
      case RAW_BACKGROUND:
        executor = new RawFunctionExecutor((RawBackgroundFunction) instance);
        break;
      case BACKGROUND:
        BackgroundFunction<?> backgroundFunction = (BackgroundFunction<?>) instance;
        @SuppressWarnings("unchecked")
        Class<? extends BackgroundFunction<?>> c =
            (Class<? extends BackgroundFunction<?>>) backgroundFunction.getClass();
        Optional<Type> maybeTargetType = backgroundFunctionTypeArgument(c);
        if (!maybeTargetType.isPresent()) {
          // This is probably because the user implemented just BackgroundFunction rather than
          // BackgroundFunction<T>.
          throw new RuntimeException(
              "Could not determine the payload type for BackgroundFunction of type "
                  + instance.getClass().getName()
                  + "; must implement BackgroundFunction<T> for some T");
        }
        executor = new TypedFunctionExecutor<>(maybeTargetType.get(), backgroundFunction);
        break;
      case CLOUD_EVENTS:
        executor = new CloudEventFunctionExecutor((CloudEventsFunction) instance);
        break;
      default: // can't happen, we've listed all the FunctionKind values already.
        throw new AssertionError(functionKind);
    }
    return new BackgroundFunctionExecutor(executor);
  }

  /**
   * Returns the {@code T} of a concrete class that implements {@link BackgroundFunction
   * BackgroundFunction<T>}. Returns an empty {@link Optional} if {@code T} can't be determined.
   */
  static Optional<Type> backgroundFunctionTypeArgument(
      Class<? extends BackgroundFunction<?>> functionClass) {
    // If this is BackgroundFunction<Foo> then the user must have implemented a method
    // accept(Foo, Context), so we look for that method and return the type of its first argument.
    // We must be careful because the compiler will also have added a synthetic method
    // accept(Object, Context).
    return Arrays.stream(functionClass.getMethods())
        .filter(
            m ->
                m.getName().equals("accept")
                    && m.getParameterCount() == 2
                    && m.getParameterTypes()[1] == Context.class
                    && m.getParameterTypes()[0] != Object.class)
        .map(m -> m.getGenericParameterTypes()[0])
        .findFirst();
  }

  private static Event parseLegacyEvent(HttpServletRequest req) throws IOException {
    try (BufferedReader bodyReader = req.getReader()) {
      return parseLegacyEvent(bodyReader);
    }
  }

  static Event parseLegacyEvent(Reader reader) throws IOException {
    // A Type Adapter is required to set the type of the JsonObject because CloudFunctionsContext
    // is abstract and Gson default behavior instantiates the type provided.
    TypeAdapter<CloudFunctionsContext> typeAdapter = CloudFunctionsContext.typeAdapter(new Gson());
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(CloudFunctionsContext.class, typeAdapter)
            .registerTypeAdapter(Event.class, new Event.EventDeserializer())
            .create();
    return gson.fromJson(reader, Event.class);
  }

  private static Context contextFromCloudEvent(CloudEvent cloudEvent) {
    OffsetDateTime timestamp =
        Optional.ofNullable(cloudEvent.getTime()).orElse(OffsetDateTime.now());
    String timestampString = DateTimeFormatter.ISO_INSTANT.format(timestamp);
    // We don't have an obvious replacement for the Context.resource field, which with legacy events
    // corresponded to a value present for some proprietary Google event types.
    String resource = "{}";
    Map<String, String> attributesMap =
        cloudEvent.getAttributeNames().stream()
            .collect(toMap(a -> a, a -> String.valueOf(cloudEvent.getAttribute(a))));
    return CloudFunctionsContext.builder()
        .setEventId(cloudEvent.getId())
        .setEventType(cloudEvent.getType())
        .setResource(resource)
        .setTimestamp(timestampString)
        .setAttributes(attributesMap)
        .build();
  }

  /**
   * A background function, either "raw" or "typed". A raw background function is one where the user
   * code receives a String parameter that is the JSON payload of the triggering event. A typed
   * background function is one where the payload is deserialized into a user-provided class whose
   * field names correspond to the keys of the JSON object.
   *
   * <p>In addition to these two flavours, events can be either "legacy events" or "CloudEvents".
   * Legacy events are the only kind that GCF originally supported, and use proprietary encodings
   * for the various triggers. CloudEvents are ones that follow the standards defined by <a
   * href="https://cloudevents.io">cloudevents.io</a>.
   *
   * @param <CloudEventDataT> the type to be used in the {@link Unmarshallers} call when
   *     unmarshalling this event, if it is a CloudEvent.
   */
  private abstract static class FunctionExecutor<CloudEventDataT> {
    private final Class<?> functionClass;

    FunctionExecutor(Class<?> functionClass) {
      this.functionClass = functionClass;
    }

    final String functionName() {
      return functionClass.getCanonicalName();
    }

    final ClassLoader functionClassLoader() {
      return functionClass.getClassLoader();
    }

    abstract void serviceLegacyEvent(Event legacyEvent) throws Exception;

    abstract void serviceCloudEvent(CloudEvent cloudEvent) throws Exception;
  }

  private static class RawFunctionExecutor extends FunctionExecutor<Map<?, ?>> {
    private static Gson gson = new GsonBuilder().serializeNulls().create();
    private final RawBackgroundFunction function;

    RawFunctionExecutor(RawBackgroundFunction function) {
      super(function.getClass());
      this.function = function;
    }

    @Override
    void serviceLegacyEvent(Event legacyEvent) throws Exception {
      function.accept(gson.toJson(legacyEvent.getData()), legacyEvent.getContext());
    }

    @Override
    void serviceCloudEvent(CloudEvent cloudEvent) throws Exception {
      serviceLegacyEvent(CloudEvents.convertToLegacyEvent(cloudEvent));
    }
  }

  private static class TypedFunctionExecutor<T> extends FunctionExecutor<T> {
    private final Type type; // T
    private final BackgroundFunction<T> function;

    private TypedFunctionExecutor(Type type, BackgroundFunction<T> function) {
      super(function.getClass());
      this.type = type;
      this.function = function;
    }

    static <T> TypedFunctionExecutor<T> of(Type type, BackgroundFunction<?> instance) {
      @SuppressWarnings("unchecked")
      BackgroundFunction<T> function = (BackgroundFunction<T>) instance;
      return new TypedFunctionExecutor<>(type, function);
    }

    @Override
    void serviceLegacyEvent(Event legacyEvent) throws Exception {
      T payload = new Gson().fromJson(legacyEvent.getData(), type);
      function.accept(payload, legacyEvent.getContext());
    }

    @Override
    void serviceCloudEvent(CloudEvent cloudEvent) throws Exception {
      if (cloudEvent.getData() != null) {
        serviceLegacyEvent(CloudEvents.convertToLegacyEvent(cloudEvent));
      } else {
        throw new IllegalStateException("Event has no \"data\" component");
      }
    }
  }

  private static class CloudEventFunctionExecutor extends FunctionExecutor<Void> {
    private final CloudEventsFunction function;

    CloudEventFunctionExecutor(CloudEventsFunction function) {
      super(function.getClass());
      this.function = function;
    }

    @Override
    void serviceLegacyEvent(Event legacyEvent) throws Exception {
      CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
      function.accept(cloudEvent);
    }

    @Override
    void serviceCloudEvent(CloudEvent cloudEvent) throws Exception {
      function.accept(cloudEvent);
    }
  }

  /** Executes the user's background function. This can handle all HTTP methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String contentType = req.getContentType();
    try {
      if ((contentType != null && contentType.startsWith("application/cloudevents+json"))
          || req.getHeader("ce-specversion") != null) {
        serviceCloudEvent(req);
      } else {
        serviceLegacyEvent(req);
      }
      res.setStatus(HttpServletResponse.SC_OK);
    } catch (Throwable t) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      logger.log(Level.SEVERE, "Failed to execute " + functionExecutor.functionName(), t);
    }
  }

  private enum CloudEventKind {
    BINARY,
    STRUCTURED
  }

  /**
   * Service a CloudEvent.
   *
   * @param <CloudEventT> a fake type parameter, which corresponds to the type parameter of {@link
   *     FunctionExecutor}.
   */
  private <CloudEventT> void serviceCloudEvent(HttpServletRequest req) throws Exception {
    @SuppressWarnings("unchecked")
    FunctionExecutor<CloudEventT> executor = (FunctionExecutor<CloudEventT>) functionExecutor;
    byte[] body = req.getInputStream().readAllBytes();
    MessageReader reader = HttpMessageFactory.createReaderFromMultimap(headerMap(req), body);
    // It's important not to set the context ClassLoader earlier, because MessageUtils will use
    // ServiceLoader.load(EventFormat.class) to find a handler to deserialize a binary CloudEvent
    // and if it finds something from the function ClassLoader then that something will implement
    // the EventFormat interface as defined by that ClassLoader rather than ours. Then
    // ServiceLoader.load
    // will throw ServiceConfigurationError. At this point we're still running with the default
    // context ClassLoader, which is the system ClassLoader that has loaded the code here.
    runWithContextClassLoader(() -> executor.serviceCloudEvent(reader.toEvent(data -> data)));
    // The data->data is a workaround for a bug fixed since Milestone 4 of the SDK, in
    // https://github.com/cloudevents/sdk-java/pull/259.
  }

  private static Map<String, List<String>> headerMap(HttpServletRequest req) {
    Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (String header : Collections.list(req.getHeaderNames())) {
      for (String value : Collections.list(req.getHeaders(header))) {
        headerMap.computeIfAbsent(header, unused -> new ArrayList<>()).add(value);
      }
    }
    return headerMap;
  }

  private void serviceLegacyEvent(HttpServletRequest req) throws Exception {
    Event event = parseLegacyEvent(req);
    runWithContextClassLoader(() -> functionExecutor.serviceLegacyEvent(event));
  }

  private void runWithContextClassLoader(ContextClassLoaderTask task) throws Exception {
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(functionExecutor.functionClassLoader());
      task.run();
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader);
    }
  }

  @FunctionalInterface
  private interface ContextClassLoaderTask {
    void run() throws Exception;
  }
}
