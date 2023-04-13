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

  public static TypedFunctionExecutor forClass(Class<?> maybeFunctionClazz) {

    if (!TypedFunction.class.isAssignableFrom(maybeFunctionClazz)) {
      throw new RuntimeException(
          "Class "
              + maybeFunctionClazz.getName()
              + " does not implement "
              + TypedFunction.class.getName());
    }
    @SuppressWarnings("unchecked")
    Class<? extends TypedFunction<?, ?>> clazz =
        (Class<? extends TypedFunction<?, ?>>) maybeFunctionClazz.asSubclass(TypedFunction.class);

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
        .filter(m -> m.getName().equals("handle") && m.getParameterCount() == 1)
        .map(m -> m.getGenericParameterTypes()[0])
        .findFirst();
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    HttpRequestImpl reqImpl = new HttpRequestImpl(req);
    HttpResponseImpl respImpl = new HttpResponseImpl(res);
    ClassLoader oldContextLoader = Thread.currentThread().getContextClassLoader();

    try {
      Thread.currentThread().setContextClassLoader(func.getClass().getClassLoader());

      WireFormat format = configuration.getFormat();

      Object reqObj = null;
      try {
        reqObj = format.deserialize(reqImpl, argType);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Failed to parse request for " + func.getClass().getName(), e);
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }

      Object respObj = null;
      try {
        respObj = this.func.handle(reqObj);
      } catch (Throwable t) {
        logger.log(Level.SEVERE, "Failed to execute " + func.getClass().getName(), t);
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }

      try {
        format.serialize(respObj, respImpl);
      } catch (Exception e) {
        logger.log(
            Level.SEVERE, "Failed to serialize response for " + func.getClass().getName(), e);
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      }

    } finally {
      Thread.currentThread().setContextClassLoader(oldContextLoader);
      try {
        // We can't use HttpServletResponse.flushBuffer() because we wrap the
        // PrintWriter
        // returned by HttpServletResponse in our own BufferedWriter to match our API.
        // So we have to flush whichever of getWriter() or getOutputStream() works.
        try {
          respImpl.getOutputStream().flush();
        } catch (IllegalStateException e) {
          respImpl.getWriter().flush();
        }
      } catch (IOException e) {
        // Too bad, can't flush.
      }
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
