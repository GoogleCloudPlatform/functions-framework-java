// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.functions.invoker.http;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class TimeoutHandler extends Handler.Wrapper {
  private final int timeoutMs;

  public TimeoutHandler(int timeoutSeconds, Handler handler) {
    setHandler(handler);
    this.timeoutMs = timeoutSeconds * 1000; // Convert seconds to milliseconds
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    // Wrap the callback to ensure it is only called once between the handler and the timeout task.
    AtomicBoolean completed = new AtomicBoolean(false);
    Callback wrappedCallback = new Callback() {
      @Override
      public void succeeded() {
        if (completed.compareAndSet(false, true)) {
          callback.succeeded();
        }
      }

      @Override
      public void failed(Throwable x) {
        if (completed.compareAndSet(false, true)) {
            callback.failed(x);
        }
      }

      @Override
      public InvocationType getInvocationType() {
        return callback.getInvocationType();
      }
    };

    // TODO: consider wrapping the request/response to throw if they are used after timeout.
    // TODO: Use org.eclipse.jetty.io.CyclicTimeouts which is optimized for timeouts which are almost always cancelled.
    Timer timer = new Timer(true);
    TimerTask timeoutTask =
        new TimerTask() {
          @Override
          public void run() {
            // TODO: there is a race between the handler writing response and timeout firing.
            //  This timeout firing doesn't stop the thread handling the request / response it just writes an error to the response.
            Response.writeError(
                request,
                response,
                callback,
                HttpStatus.REQUEST_TIMEOUT_408,
                "Function execution timed out");
          }
        };

    timer.schedule(timeoutTask, timeoutMs);

    boolean handle;
    try {
      handle = super.handle(request, response, wrappedCallback);
      timeoutTask.cancel();
    } finally {
      timer.purge();
    }

    return handle;
  }
}
