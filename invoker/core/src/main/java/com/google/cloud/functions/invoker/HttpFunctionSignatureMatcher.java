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
