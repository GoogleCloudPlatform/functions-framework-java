package com.google.cloud.functions.invoker;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's background function. */
public class NewBackgroundFunctionExecutor extends HttpServlet {
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final RawBackgroundFunction function;

  private NewBackgroundFunctionExecutor(RawBackgroundFunction function) {
    this.function = function;
  }

  /**
   * Make a {@link NewBackgroundFunctionExecutor} for the class named by the given {@code target}.
   * If the class cannot be loaded, we currently assume that this is an old-style function
   * (specified as package.Class.method instead of package.Class) and return
   * {@code Optional.empty()}.
   *
   * @throws RuntimeException if we succeed in loading the class named by {@code target} but then
   *    either the class does not implement {@link RawBackgroundFunction} or we are unable to
   *    construct an instance using its no-arg constructor.
   */
  public static Optional<NewBackgroundFunctionExecutor> forTarget(String target) {
    Class<?> c;
    try {
      c = Class.forName(target);
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
    if (!BackgroundFunction.class.isAssignableFrom(c)
        && !RawBackgroundFunction.class.isAssignableFrom(c)) {
      throw new RuntimeException(
          "Class " + c.getName() + " implements neither " + BackgroundFunction.class
              .getName() + " nor " + RawBackgroundFunction.class.getName());
    }
    Object instance;
    try {
      instance = c.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Could not construct an instance of " + target + ": " + e, e);
    }
    RawBackgroundFunction function =
        (instance instanceof RawBackgroundFunction)
            ? (RawBackgroundFunction) instance
            : asRaw((BackgroundFunction<?>) instance);
    return Optional.of(new NewBackgroundFunctionExecutor(function));
  }

  private static <T> RawBackgroundFunction asRaw(BackgroundFunction<T> backgroundFunction) {
    Optional<Type> maybeTargetType = backgroundFunctionTypeArgument(backgroundFunction.getClass());
    if (!maybeTargetType.isPresent()) {
      // This is probably because the user implemented just BackgroundFunction rather than
      // BackgroundFunction<T>.
      throw new RuntimeException(
          "Could not determine the payload type for BackgroundFunction of type "
              + backgroundFunction.getClass().getName()
              + "; must implement BackgroundFunction<T> for some T");
    }
    return new AsRaw<T>(maybeTargetType.get(), backgroundFunction);
  }

  /**
   * Returns the {@code T} of a concrete class that implements
   * {@link BackgroundFunction BackgroundFunction<T>}. Returns an empty {@link Optional} if
   * {@code T} can't be determined.
   */
  static Optional<Type> backgroundFunctionTypeArgument(
      Class<? extends BackgroundFunction> functionClass) {
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

  /**
   * Wraps a typed {@link BackgroundFunction} as a {@link RawBackgroundFunction} that takes its
   * input JSON string and deserializes it into the payload type of the {@link BackgroundFunction}/
   */
  private static class AsRaw<T> implements RawBackgroundFunction {
    private final Gson gson = new Gson();
    private final Type targetType;
    private final BackgroundFunction<T> backgroundFunction;

    private AsRaw(Type targetType, BackgroundFunction<T> backgroundFunction) {
      this.targetType = targetType;
      this.backgroundFunction = backgroundFunction;
    }

    @Override
    public void accept(String json, Context context) {
      T payload;
      try {
        payload = gson.fromJson(json, targetType);
      } catch (JsonParseException e) {
        logger.log(Level.WARNING,
            "Could not convert payload to target type " + targetType.getTypeName(), e);
        return;
      }
      backgroundFunction.accept(payload, context);
    }
  }

  /** Executes the user's background function, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    BufferedReader body = req.getReader();

    // A Type Adapter is required to set the type of the JsonObject because CloudFunctionsContext
    // is abstract and Gson default behavior instantiates the type provided.
    TypeAdapter<CloudFunctionsContext> typeAdapter =
        CloudFunctionsContext.typeAdapter(new Gson());
    Gson gson = new GsonBuilder()
        .registerTypeAdapter(CloudFunctionsContext.class, typeAdapter)
        .registerTypeAdapter(Event.class, new Event.EventDeserializer())
        .create();

    Event event = gson.fromJson(body, Event.class);
    try {
      function.accept(gson.toJson(event.getData()), event.getContext());
      res.setStatus(HttpServletResponse.SC_OK);
    } catch (Throwable t) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      logger.log(Level.WARNING, "Failed to execute " + function.getClass().getName(), t);
    }
  }
}
