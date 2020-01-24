package com.google.cloud.functions.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Wrapper for user HTTP function. */
public class HttpCloudFunction extends CloudFunction {

  public HttpCloudFunction(Object o, Method m) {
    super(o, m);
  }

  public void execute(HttpServletRequest req, HttpServletResponse res)
      throws InvocationTargetException {
    rawExecute(req, res);
  }
}
