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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's method. */
public class HttpFunctionExecutor extends HttpServlet {
  private final HttpCloudFunction function;

  public HttpFunctionExecutor(HttpCloudFunction function) {
    this.function = function;
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    URLRequestWrapper wrapper = new URLRequestWrapper(req);
    try {
      function.execute(wrapper, res);
    } catch (InvocationTargetException e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      e.getCause().printStackTrace();
    }
  }
}
