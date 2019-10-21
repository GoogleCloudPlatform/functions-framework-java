package com.google.cloud.functions.invoker;


/**
 * An interface for classes that contain logic for matching user function method signature with
 * one of the supported signatures.
 *
 * @param <T> subtype of CloudFunction that is matched
 */
public interface FunctionSignatureMatcher<T extends CloudFunction> {

  /**
   * Matches user function method with given name with the expected signature.
   *
   * @param functionClass user function class
   * @param functionInstance instance of user function class
   * @param functionMethodName name of the user function method specified in function target
   * @param functionTarget
   * @return instance of T if successfully matched
   */
  T match(
      Class<?> functionClass,
      Object functionInstance,
      String functionMethodName,
      String functionTarget)
      throws RuntimeException;
}
