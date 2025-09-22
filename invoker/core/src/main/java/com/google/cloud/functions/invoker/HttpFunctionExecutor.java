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

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.invoker.gcf.ExecutionIdUtil;
import com.google.cloud.functions.invoker.http.HttpRequestImpl;
import com.google.cloud.functions.invoker.http.HttpResponseImpl;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/** Executes the user's method. */
public class HttpFunctionExecutor extends Handler.Abstract {
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final HttpFunction function;
  private final ExecutionIdUtil executionIdUtil = new ExecutionIdUtil();

  private HttpFunctionExecutor(HttpFunction function) {
    this.function = function;
  }

  /**
   * Makes a {@link HttpFunctionExecutor} for the given class.
   *
   * @throws RuntimeException if either the given class does not implement {@link HttpFunction} or
   *     we are unable to construct an instance using its no-arg constructor.
   */
  public static HttpFunctionExecutor forClass(Class<?> functionClass) {
    if (!HttpFunction.class.isAssignableFrom(functionClass)) {
      throw new RuntimeException(
          "Class "
              + functionClass.getName()
              + " does not implement "
              + HttpFunction.class.getName());
    }
    Class<? extends HttpFunction> httpFunctionClass = functionClass.asSubclass(HttpFunction.class);
    ClassLoader oldContextLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(httpFunctionClass.getClassLoader());
      HttpFunction httpFunction = httpFunctionClass.getConstructor().newInstance();
      return new HttpFunctionExecutor(httpFunction);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Could not construct an instance of " + functionClass.getName() + ": " + e, e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextLoader);
    }
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {

    HttpRequestImpl reqImpl = new HttpRequestImpl(request);
    HttpResponseImpl respImpl = new HttpResponseImpl(response);
    ClassLoader oldContextLoader = Thread.currentThread().getContextClassLoader();
    try {
      executionIdUtil.storeExecutionId(req);
      Thread.currentThread().setContextClassLoader(function.getClass().getClassLoader());
      function.service(reqImpl, respImpl);
      respImpl.close(callback);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to execute " + function.getClass().getName(), t);
      if (response.isCommitted()) {
        callback.failed(t);
      } else {
        response.reset();
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
        callback.succeeded();
      }
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextLoader);
      executionIdUtil.removeExecutionId();
    }
    return true;
  }
}
