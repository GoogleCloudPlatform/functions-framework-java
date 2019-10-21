package com.google.cloud.functions.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A container for user function method and an instance of the associated class.
 * It allows to execute the function via {@link #rawExecute(Object...)}.
 */
class CloudFunction {
  final Object functionObject;
  final Method functionMethod;

  CloudFunction(Object functionObject, Method functionMethod) {
    this.functionObject = functionObject;
    this.functionMethod = functionMethod;

    if (!functionMethod.getDeclaringClass().isAssignableFrom(functionObject.getClass())) {
      throw new RuntimeException("Internal error: function object and method type mismatch");
    }
  }

  Object rawExecute(Object... args) throws InvocationTargetException {
    try {
      return functionMethod.invoke(functionObject, args);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Could not access function method: is it public?", e);
    }
  }
}
