package com.google.cloud.functions.invoker;

import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Implements {@link FunctionSignatureMatcher} for HTTP functions with signature:
 * void function(HttpServletRequest req, HttpServletResponse res);
 */
public class HttpFunctionSignatureMatcher implements FunctionSignatureMatcher<HttpCloudFunction> {

  @Override
  public HttpCloudFunction match(
      Class<?> functionClass,
      Object functionInstance,
      String functionMethodName,
      String functionTarget) {
    Method functionMethod;
    try {
      functionMethod =
          functionClass.getMethod(
              functionMethodName, HttpServletRequest.class, HttpServletResponse.class);
    } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            String.format(
                "%1$s didn't match any of the supported handler signatures for HTTP functions."
                    + " Expected method signature of the form:"
                    + " %1$s(javax.servlet.http.HttpServletRequest,"
                    + " javax.servlet.http.HttpServletResponse)",
                functionTarget));
    }
    return new HttpCloudFunction(functionInstance, functionMethod);
  }
}
