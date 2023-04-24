package com.google.cloud.functions.invoker;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.functions.TypedFunction;
import com.google.cloud.functions.TypedFunction.Configuration;
import com.google.cloud.functions.TypedFunction.WireFormat;
import com.google.cloud.functions.invoker.http.HttpRequestImpl;
import com.google.cloud.functions.invoker.http.HttpResponseImpl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TypedFunctionExecutor extends HttpServlet {
  private static final String APPLY_METHOD = "apply";
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final Type argType;
  private final TypedFunction<Object, Object> func;
  private final ConfigurationImpl configuration;

  private TypedFunctionExecutor(
      Type argType, TypedFunction<Object, Object> func, ConfigurationImpl configuration) {
    this.argType = argType;
    this.func = func;
    this.configuration = configuration;
  }

  public static TypedFunctionExecutor forClass(Class<?> genericClass) {
    if (!TypedFunction.class.isAssignableFrom(genericClass)) {
      throw new RuntimeException(
          "Class "
              + genericClass.getName()
              + " does not implement "
              + TypedFunction.class.getName());
    }
    @SuppressWarnings("unchecked")
    Class<? extends TypedFunction<?, ?>> clazz =
        (Class<? extends TypedFunction<?, ?>>) genericClass.asSubclass(TypedFunction.class);

    Optional<Type> argType = TypedFunctionExecutor.handlerTypeArgument(clazz);
    if (argType.isEmpty()) {
      throw new RuntimeException(
          "Class " + clazz.getName() + " does not implement " + TypedFunction.class.getName());
    }

    TypedFunction<?, ?> funcInstance;
    try {
      funcInstance = clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(
          "Class "
              + clazz.getName()
              + " must declare a valid default constructor to be usable as a strongly typed"
              + " function. Could not use constructor: "
              + e.toString());
    }

    ConfigurationImpl configuration = new ConfigurationImpl();
    funcInstance.configure(configuration);

    @SuppressWarnings("unchecked")
    TypedFunctionExecutor executor =
        new TypedFunctionExecutor(
            argType.orElseThrow(), (TypedFunction<Object, Object>) funcInstance, configuration);
    return executor;
  }

  /**
   * Returns the {@code T} of a concrete class that implements {@link BackgroundFunction
   * BackgroundFunction<T>}. Returns an empty {@link Optional} if {@code T} can't be determined.
   */
  static Optional<Type> handlerTypeArgument(Class<? extends TypedFunction<?, ?>> functionClass) {
    return Arrays.stream(functionClass.getMethods())
        .filter(method -> method.getName().equals(APPLY_METHOD) && method.getParameterCount() == 1)
        .map(method -> method.getGenericParameterTypes()[0])
        .findFirst();
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    HttpRequestImpl reqImpl = new HttpRequestImpl(req);
    HttpResponseImpl respImpl = new HttpResponseImpl(res);
    ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

    try {
      Thread.currentThread().setContextClassLoader(func.getClass().getClassLoader());
      handleRequest(reqImpl, respImpl);
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextClassLoader);
      flushResponse(respImpl);
    }
  }

  private void handleRequest(HttpRequest req, HttpResponse resp) {
    WireFormat format = configuration.getFormat();

    Object reqObj = null;
    try {
      reqObj = format.deserialize(req, argType);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to parse request for " + func.getClass().getName(), t);
      resp.setStatusCode(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    Object respObj = null;
    try {
      respObj = this.func.apply(reqObj);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to execute " + func.getClass().getName(), t);
      resp.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    try {
      format.serialize(respObj, resp);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to serialize response for " + func.getClass().getName(), t);
      resp.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
  }

  private void flushResponse(HttpResponseImpl respImpl) {
    try {
      // We can't use HttpServletResponse.flushBuffer() because we wrap the
      // PrintWriter returned by HttpServletResponse in our own BufferedWriter
      // to match our API. So we have to flush whichever of getWriter() or
      // getOutputStream() works.
      try {
        respImpl.getOutputStream().flush();
      } catch (IllegalStateException e) {
        respImpl.getWriter().flush();
      }
    } catch (IOException e) {
      // Too bad, can't flush.
    }
  }

  private static class ConfigurationImpl implements TypedFunction.Configuration {
    private WireFormat format = null;

    WireFormat getFormat() {
      if (format == null) {
        synchronized (this) {
          if (format == null) {
            format = new GsonWireFormat();
          }
        }
      }

      return format;
    }

    @Override
    public Configuration setWireFormat(WireFormat format) {
      this.format = format;
      return null;
    }
  }

  private static class GsonWireFormat implements TypedFunction.WireFormat {
    private final Gson gson = new GsonBuilder().create();

    @Override
    public void serialize(Object object, HttpResponse response) throws Exception {
      if (object == null) {
        response.setStatusCode(204);
        return;
      }
      try (BufferedWriter bodyWriter = response.getWriter()) {
        gson.toJson(object, bodyWriter);
      }
    }

    @Override
    public Object deserialize(HttpRequest request, Type type) throws Exception {
      try (BufferedReader bodyReader = request.getReader()) {
        return gson.fromJson(bodyReader, type);
      }
    }
  }
}
