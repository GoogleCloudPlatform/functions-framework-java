package com.google.cloud.functions.invoker;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.functions.TypedFunction;
import com.google.cloud.functions.TypedFunction.WireFormat;
import com.google.cloud.functions.invoker.http.HttpRequestImpl;
import com.google.cloud.functions.invoker.http.HttpResponseImpl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
  private final TypedFunction<Object, Object> function;
  private final WireFormat format;

  private TypedFunctionExecutor(
      Type argType, TypedFunction<Object, Object> func, WireFormat format) {
    this.argType = argType;
    this.function = func;
    this.format = format;
  }

  public static TypedFunctionExecutor forClass(Class<?> functionClass) {
    if (!TypedFunction.class.isAssignableFrom(functionClass)) {
      throw new RuntimeException(
          "Class "
              + functionClass.getName()
              + " does not implement "
              + TypedFunction.class.getName());
    }
    @SuppressWarnings("unchecked")
    Class<? extends TypedFunction<?, ?>> clazz =
        (Class<? extends TypedFunction<?, ?>>) functionClass.asSubclass(TypedFunction.class);

    Optional<Type> argType = handlerTypeArgument(clazz);
    if (argType.isEmpty()) {
      throw new RuntimeException(
          "Class " + clazz.getName() + " does not implement " + TypedFunction.class.getName());
    }

    TypedFunction<?, ?> typedFunction;
    try {
      typedFunction = clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(
          "Class "
              + clazz.getName()
              + " must declare a valid default constructor to be usable as a strongly typed"
              + " function. Could not use constructor: "
              + e.toString());
    }

    WireFormat format = typedFunction.getWireFormat();
    if (format == null) {
      format = LazyDefaultFormatHolder.defaultFormat;
    }

    @SuppressWarnings("unchecked")
    TypedFunctionExecutor executor =
        new TypedFunctionExecutor(
            argType.orElseThrow(), (TypedFunction<Object, Object>) typedFunction, format);
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
    HttpResponseImpl resImpl = new HttpResponseImpl(res);
    ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

    try {
      Thread.currentThread().setContextClassLoader(function.getClass().getClassLoader());
      handleRequest(reqImpl, resImpl);
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextClassLoader);
      resImpl.flush();
    }
  }

  private void handleRequest(HttpRequest req, HttpResponse res) {
    Object reqObj;
    try {
      reqObj = format.deserialize(req, argType);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to parse request for " + function.getClass().getName(), t);
      res.setStatusCode(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    Object resObj;
    try {
      resObj = function.apply(reqObj);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to execute " + function.getClass().getName(), t);
      res.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    try {
      format.serialize(resObj, res);
    } catch (Throwable t) {
      logger.log(
          Level.SEVERE, "Failed to serialize response for " + function.getClass().getName(), t);
      res.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
  }

  private static class LazyDefaultFormatHolder {
    static final WireFormat defaultFormat = new GsonWireFormat();
  }

  private static class GsonWireFormat implements TypedFunction.WireFormat {
    private final Gson gson = new GsonBuilder().create();

    @Override
    public void serialize(Object object, HttpResponse response) throws Exception {
      if (object == null) {
        response.setStatusCode(HttpServletResponse.SC_NO_CONTENT);
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
