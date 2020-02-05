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

/**
 * Dynamically loads the user's function class and returns an instance of {@link CloudFunction}.
 */
public class FunctionLoader<T extends CloudFunction> {

  private final String functionTarget;
  private final ClassLoader classLoader;
  private final FunctionSignatureMatcher<T> matcher;

  public FunctionLoader(
      String functionTarget,
      ClassLoader classLoader,
      FunctionSignatureMatcher<T> matcher) {
    this.functionTarget = functionTarget;
    this.classLoader = classLoader;
    this.matcher = matcher;
  }

  /**
   * Tries to dynamically load the class containing user function using a class loader.
   * Automatically determines function signature type from the method signature and returns an
   * instance of either {@link HttpCloudFunction} or {@link BackgroundCloudFunction}.
   */
  public T loadUserFunction() throws Exception {
    int lastDotIndex = functionTarget.lastIndexOf(".");
    if (lastDotIndex == -1) {
      throw new ClassNotFoundException(functionTarget);
    }
    String targetClassName = functionTarget.substring(0, lastDotIndex);
    String targetMethodName = functionTarget.substring(lastDotIndex + 1);
    Class<?> targetClass;
    try {
      targetClass = classLoader.loadClass(targetClassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          "Could not load either " + functionTarget + " (new form) or "
              + targetClassName + " (old form)");
    }

    Object targetInstance = targetClass.getDeclaredConstructor().newInstance();
    return matcher.match(targetClass, targetInstance, targetMethodName, functionTarget);
  }
}
