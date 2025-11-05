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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.Scheduler;

public class TimeoutHandler extends Handler.Wrapper {
  private final Duration timeout;

  public TimeoutHandler(int timeoutSeconds, Handler handler) {
    setHandler(handler);
    timeout = Duration.ofSeconds(timeoutSeconds);
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    // Wrap the callback to ensure it is only completed once between the
    // handler and the timeout task.
    Callback wrappedCallback = new ProtectedCallback(callback);
    Scheduler.Task timeoutTask =
        request
            .getComponents()
            .getScheduler()
            .schedule(
                () ->
                    wrappedCallback.failed(
                        new BadMessageException(
                            HttpStatus.REQUEST_TIMEOUT_408, "Function execution timed out")),
                timeout);

    // Cancel the timeout if the request completes the callback first.
    return super.handle(request, response, Callback.from(timeoutTask::cancel, wrappedCallback));
  }

  private static class ProtectedCallback implements Callback {
    private final Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public ProtectedCallback(Callback callback) {
      this.callback = callback;
    }

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
  }
}
