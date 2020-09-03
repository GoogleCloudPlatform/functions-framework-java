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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageReader;
import io.cloudevents.core.message.impl.GenericStructuredMessageReader;
import io.cloudevents.core.message.impl.MessageUtils;
import io.cloudevents.core.message.impl.UnknownEncodingMessageReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  /**
   * Makes a {@link HttpFunctionExecutor} for the given class.
   *
   * @throws RuntimeException if either the class does not implement one of
   *    {@link BackgroundFunction} or {@link RawBackgroundFunction},
   *    or we are unable to construct an instance using its no-arg constructor.
   */
  public static BackgroundFunctionExecutor forClass(Class<?> functionClass) {
    if (!BackgroundFunction.class.isAssignableFrom(functionClass)
        && !RawBackgroundFunction.class.isAssignableFrom(functionClass)) {
      throw new RuntimeException(
          "Class " + functionClass.getName() + " implements neither " + BackgroundFunction.class
              .getName() + " nor " + RawBackgroundFunction.class.getName());
    }
    Object instance;
    try {
      instance = functionClass.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Could not construct an instance of " + functionClass.getName() + ": " + e, e);
    }
    FunctionExecutor<?> executor;
    if (instance instanceof RawBackgroundFunction) {
      executor = new RawFunctionExecutor((RawBackgroundFunction) instance);
    } else {
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
    }
    return new BackgroundFunctionExecutor(executor);
  }

  /**
   * Returns the {@code T} of a concrete class that implements
   * {@link BackgroundFunction BackgroundFunction<T>}. Returns an empty {@link Optional} if
   * {@code T} can't be determined.
   */
  static Optional<Type> backgroundFunctionTypeArgument(
      Class<? extends BackgroundFunction<?>> functionClass) {
    // If this is BackgroundFunction<Foo> then the user must have implemented a method
    // accept(Foo, Context), so we look for that method and return the type of its first argument.
    // We must be careful because the compiler will also have added a synthetic method
    // accept(Object, Context).
    return Arrays.stream(functionClass.getMethods())
        .filter(m -> m.getName().equals("accept") && m.getParameterCount() == 2
            && m.getParameterTypes()[1] == Context.class
            && m.getParameterTypes()[0] != Object.class)
        .map(m -> m.getGenericParameterTypes()[0])
        .findFirst();
  }

  private static Event parseLegacyEvent(HttpServletRequest req) throws IOException {
    try (BufferedReader bodyReader = req.getReader()) {
      // A Type Adapter is required to set the type of the JsonObject because CloudFunctionsContext
      // is abstract and Gson default behavior instantiates the type provided.
      TypeAdapter<CloudFunctionsContext> typeAdapter =
          CloudFunctionsContext.typeAdapter(new Gson());
      Gson gson = new GsonBuilder()
          .registerTypeAdapter(CloudFunctionsContext.class, typeAdapter)
          .registerTypeAdapter(Event.class, new Event.EventDeserializer())
          .create();
      return gson.fromJson(bodyReader, Event.class);
    }
  }

  private static Context contextFromCloudEvent(CloudEvent cloudEvent) {
    OffsetDateTime timestamp = Optional.ofNullable(cloudEvent.getTime()).orElse(OffsetDateTime.now());
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
   * for the various triggers. CloudEvents are ones that follow the standards defined by
   * <a href="https://cloudevents.io">cloudevents.io</a>.
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

    abstract void serviceLegacyEvent(HttpServletRequest req)
        throws Exception;

    abstract void serviceCloudEvent(CloudEvent cloudEvent) throws Exception;

    abstract Class<CloudEventDataT> cloudEventDataType();
  }

  private static class RawFunctionExecutor extends FunctionExecutor<Map<?, ?>> {
    private final RawBackgroundFunction function;

    RawFunctionExecutor(RawBackgroundFunction function) {
      super(function.getClass());
      this.function = function;
    }

    @Override
    void serviceLegacyEvent(HttpServletRequest req) throws Exception {
      Event event = parseLegacyEvent(req);
      function.accept(new Gson().toJson(event.getData()), event.getContext());
    }

    @Override
    void serviceCloudEvent(CloudEvent cloudEvent) throws Exception {
      Context context = contextFromCloudEvent(cloudEvent);
      String jsonData = cloudEvent.getData() == null ? "{}" : new String(cloudEvent.getData(), UTF_8);
      function.accept(jsonData, context);
    }

    @Override
    Class<Map<?, ?>> cloudEventDataType() {
      // This messing about with casts and @SuppressWarnings allows us to limit the use of the raw
      // Map type to just here.
      @SuppressWarnings("unchecked")
      Class<Map<?, ?>> c = (Class<Map<?, ?>>) (Class<?>) Map.class;
      return c;
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
    void serviceLegacyEvent(HttpServletRequest req) throws Exception {
      Event event = parseLegacyEvent(req);
      T payload = new Gson().fromJson(event.getData(), type);
      function.accept(payload, event.getContext());
    }

    @Override
    void serviceCloudEvent(CloudEvent cloudEvent) throws Exception {
      if (cloudEvent.getData() != null) {
        String data = new String(cloudEvent.getData(), UTF_8);
        T payload = new Gson().fromJson(data, type);
        Context context = contextFromCloudEvent(cloudEvent);
        function.accept(payload, context);
      } else {
        throw new IllegalStateException("Event has no \"data\" component");
      }
    }

    @Override
    Class<T> cloudEventDataType() {
      if (!(type instanceof Class<?>)) {
        throw new IllegalStateException(
            "CloudEvents SDK currently does not permit deserializing types other than classes:"
            + " cannot deserialize " + type);
      }
      @SuppressWarnings("unchecked")
      Class<T> c = (Class<T>) type;
      return c;
    }
  }

  /** Executes the user's background function. This can handle all HTTP methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String contentType = req.getContentType();
    ClassLoader oldContextLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(functionExecutor.functionClassLoader());
      if ((contentType != null && contentType.startsWith("application/cloudevents+json"))
          || req.getHeader("ce-specversion") != null) {
        serviceCloudEvent(req);
      } else {
        serviceLegacyEvent(req);
      }
      res.setStatus(HttpServletResponse.SC_OK);
    } catch (Throwable t) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      logger.log(Level.WARNING, "Failed to execute " + functionExecutor.functionName(), t);
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextLoader);
    }
  }

  private enum CloudEventKind {BINARY, STRUCTURED}

  /**
   * Service a CloudEvent.
   *
   * @param <CloudEventT> a fake type parameter, which corresponds to the type parameter of
   *     {@link FunctionExecutor}.
   */
  private <CloudEventT> void serviceCloudEvent(HttpServletRequest req) throws Exception {
    @SuppressWarnings("unchecked")
    FunctionExecutor<CloudEventT> executor = (FunctionExecutor<CloudEventT>) functionExecutor;
    Map<String, List<String>> headers = CloudEventsServletBinaryMessageReader.headerMap(req);
    byte[] body = req.getInputStream().readAllBytes();
    List<String> listOfNull = Collections.singletonList(null);
    MessageReader reader = MessageUtils.parseStructuredOrBinaryMessage(
        () -> headers.getOrDefault("content-type", listOfNull).get(0),
        format -> new GenericStructuredMessageReader(format, body),
        () -> headers.getOrDefault("ce-specversion", listOfNull).get(0),
        unusedSpecVersion -> CloudEventsServletBinaryMessageReader.from(req, body),
        UnknownEncodingMessageReader::new);
    executor.serviceCloudEvent(reader.toEvent());
  }

  private void serviceLegacyEvent(HttpServletRequest req) throws Exception {
    functionExecutor.serviceLegacyEvent(req);
  }
}
