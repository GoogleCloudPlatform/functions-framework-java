package com.google.cloud.functions.invoker;

import java.lang.reflect.InvocationTargetException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's method. */
public class HttpFunctionExecutor extends HttpServlet {
  private final HttpCloudFunction function;

  public HttpFunctionExecutor(HttpCloudFunction function) {
    this.function = function;
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    URLRequestWrapper wrapper = new URLRequestWrapper(req);
    try {
      function.execute(wrapper, res);
    } catch (InvocationTargetException e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      e.getCause().printStackTrace();
    }
  }
}
