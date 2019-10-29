package com.google.cloud.functions.invoker;

import com.google.cloud.functions.BackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's background function. */
public class NewBackgroundFunctionExecutor extends HttpServlet {
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final BackgroundFunction function;

  private NewBackgroundFunctionExecutor(BackgroundFunction function) {
    this.function = function;
  }

  /**
   * Make a {@link NewBackgroundFunctionExecutor} for the class named by the given {@code target}.
   * If the class cannot be loaded, we currently assume that this is an old-style function
   * (specified as package.Class.method instead of package.Class) and return
   * {@code Optional.empty()}.
   *
   * @throws RuntimeException if we succeed in loading the class named by {@code target} but then
   *    either the class does not implement {@link HttpFunction} or we are unable to construct an
   *    instance using its no-arg constructor.
   */
  public static Optional<NewBackgroundFunctionExecutor> forTarget(String target) {
    Class<?> c;
    try {
      c = Class.forName(target);
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
    if (!BackgroundFunction.class.isAssignableFrom(c)) {
      throw new RuntimeException(
          "Class " + c.getName() + " does not implement " + BackgroundFunction.class.getName());
    }
    Class<? extends BackgroundFunction> functionClass = c.asSubclass(BackgroundFunction.class);
    try {
      BackgroundFunction function = functionClass.getConstructor().newInstance();
      return Optional.of(new NewBackgroundFunctionExecutor(function));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Could not construct an instance of " + target + ": " + e, e);
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
      function.accept(event.getData(), event.getContext());
      res.setStatus(HttpServletResponse.SC_OK);
    } catch (Throwable t) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      logger.log(Level.WARNING, "Failed to execute " + function.getClass().getName(), t);
    }
  }
}
