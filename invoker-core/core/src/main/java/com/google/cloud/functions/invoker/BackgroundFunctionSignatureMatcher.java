package com.google.cloud.functions.invoker;

import com.google.cloud.functions.Context;
import java.lang.reflect.Method;

/**
 * Implements {@link FunctionSignatureMatcher} for background functions. A valid background
 * functions has a first parameter that can be of any type (JSON payload is deserialized into this
 * parameter), and an optional second parameter of type {@link Context}.
 */
public class BackgroundFunctionSignatureMatcher
    implements FunctionSignatureMatcher<BackgroundCloudFunction> {

  @Override
  public BackgroundCloudFunction match(
      Class<?> functionClass,
      Object functionInstance,
      String functionMethodName,
      String functionTarget) {

    for (Method method : functionClass.getMethods()) {
      if (method.getName().equals(functionMethodName)) {
        switch (method.getParameterCount()) {
          case 1:
            break;
          case 2:
            if (method.getParameterTypes()[1] == Context.class) {
              break;
            }
            continue;
          default:
            continue;
        }
      }
      return new BackgroundCloudFunction(functionInstance, method);
    }
    throw new RuntimeException(
        String.format(
            "%1$s didn't match any of the supported handler signatures for background functions."
                + " Expected method signature of the form:"
                + " %1$s(com.google.gson.JsonElement [, com.google.functions.Context]) or"
                + " %1$s(your.CustomType [, com.google.functions.Context])",
            functionTarget));
  }
}
