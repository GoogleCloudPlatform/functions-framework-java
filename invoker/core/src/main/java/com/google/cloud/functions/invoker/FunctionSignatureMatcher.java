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
