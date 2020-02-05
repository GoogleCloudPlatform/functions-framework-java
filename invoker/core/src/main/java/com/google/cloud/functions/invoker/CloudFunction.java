// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
