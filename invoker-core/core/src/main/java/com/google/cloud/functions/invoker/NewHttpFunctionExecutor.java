package com.google.cloud.functions.invoker;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.invoker.http.HttpRequestImpl;
import com.google.cloud.functions.invoker.http.HttpResponseImpl;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's method. */
public class NewHttpFunctionExecutor extends HttpServlet {
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final HttpFunction function;

  private NewHttpFunctionExecutor(HttpFunction function) {
    this.function = function;
  }

  /**
   * Make a {@link NewHttpFunctionExecutor} for the class named by the given {@code target}.
   * If the class cannot be loaded, we currently assume that this is an old-style function
   * (specified as package.Class.method instead of package.Class) and return
   * {@code Optional.empty()}.
   *
   * @throws RuntimeException if we succeed in loading the class named by {@code target} but then
   *    either the class does not implement {@link HttpFunction} or we are unable to construct an
   *    instance using its no-arg constructor.
   */
  public static Optional<NewHttpFunctionExecutor> forTarget(String target, ClassLoader loader) {
    Class<?> c;
    while (true) {
      try {
        c = loader.loadClass(target);
        break;
      } catch (ClassNotFoundException e) {
        // This might be a nested class like com.example.Foo.Bar. That will actually appear as
        // com.example.Foo$Bar as far as Class.forName is concerned. So we try to replace every dot
        // from the last to the first with a $ in the hope of finding a class we can load.
        int lastDot = target.lastIndexOf('.');
        if (lastDot < 0) {
          return Optional.empty();
        }
        target = target.substring(0, lastDot) + '$' + target.substring(lastDot + 1);
      }
    }
    if (!HttpFunction.class.isAssignableFrom(c)) {
      throw new RuntimeException(
          "Class " + c.getName() + " does not implement " + HttpFunction.class.getName());
    }
    Class<? extends HttpFunction> httpFunctionClass = c.asSubclass(HttpFunction.class);
    try {
      HttpFunction httpFunction = httpFunctionClass.getConstructor().newInstance();
      return Optional.of(new NewHttpFunctionExecutor(httpFunction));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Could not construct an instance of " + target + ": " + e, e);
    }
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    URLRequestWrapper wrapper = new URLRequestWrapper(req);
    HttpRequestImpl reqImpl = new HttpRequestImpl(wrapper);
    HttpResponseImpl respImpl = new HttpResponseImpl(res);
    try {
      function.service(reqImpl, respImpl);
    } catch (Throwable t) {
      // TODO(b/146510646): this should be logged properly as an exception, but that currently
      //   causes integration tests to fail.
      // logger.log(Level.WARNING, "Failed to execute " + function.getClass().getName(), t);
      logger.log(Level.WARNING, "Failed to execute {0}", function.getClass().getName());
      t.printStackTrace();
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      try {
        respImpl.getWriter().flush();
      } catch (IOException e) {
        // Too bad, can't flush.
      }
    }
  }
}
