package com.google.cloud.functions.invoker;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

/**
 * Dynamically loads the user's function class and returns an instance of {@link CloudFunction}.
 */
public class FunctionLoader<T extends CloudFunction> {

  private final String functionTarget;
  private final FunctionSignatureMatcher<T> matcher;
  private final Optional<File> userJarFile;

  public FunctionLoader(
      String functionTarget,
      Optional<File> userJarFile,
      FunctionSignatureMatcher<T> matcher) {
    this.functionTarget = functionTarget;
    this.matcher = matcher;
    this.userJarFile = userJarFile;
  }

  /**
   * Tries to dynamically load the class containing user function using a class loader.
   * Automatically determines function signature type from the method signature and returns an
   * instance of either {@link HttpCloudFunction} or {@link BackgroundCloudFunction}.
   */
  public T loadUserFunction() throws Exception {
    int lastDotIndex = functionTarget.lastIndexOf(".");
    if (lastDotIndex == -1) {
      throw new RuntimeException(
          "Expected target of format <package>.<class>.<method>, but got " + functionTarget);
    }
    String targetClassName = functionTarget.substring(0, lastDotIndex);
    String targetMethodName = functionTarget.substring(lastDotIndex + 1);

    ClassLoader classLoader;

    if (userJarFile.isPresent()) {
      classLoader =
          new URLClassLoader(
              new URL[]{userJarFile.get().toURI().toURL()},
              Thread.currentThread().getContextClassLoader());
    } else {
      classLoader = Thread.currentThread().getContextClassLoader();
    }
    Class<?> targetClass = classLoader.loadClass(targetClassName);

    Object targetInstance = targetClass.getDeclaredConstructor().newInstance();
    return matcher.match(targetClass, targetInstance, targetMethodName, functionTarget);
  }
}
