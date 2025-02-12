package com.google.cloud.functions.invoker.gcf;

import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Handler;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;

/**
 * A helper class that either fetches a unique execution id from request HTTP headers or generates a
 * random id.
 */
public final class ExecutionIdUtil {
  private static final Logger rootLogger = Logger.getLogger("");
  private static final int EXECUTION_ID_LENGTH = 12;
  private static final String EXECUTION_ID_HTTP_HEADER = "HTTP_FUNCTION_EXECUTION_ID";
  private static final String LOG_EXECUTION_ID_ENV_NAME = "LOG_EXECUTION_ID";

  private final Random random = ThreadLocalRandom.current();

  /**
   * Add mapping to root logger from current thread id to execution id. This mapping will be used to
   * append the execution id to log lines.
   */
  public void storeExecutionId(HttpServletRequest request) {
    if (!executionIdLoggingEnabled()) {
      return;
    }
    for (Handler handler : rootLogger.getHandlers()) {
      if (handler instanceof JsonLogHandler) {
        String id = getOrGenerateExecutionId(request);
        ((JsonLogHandler) handler).addExecutionId(Thread.currentThread().getId(), id);
      }
    }
  }

  /** Remove mapping from curent thread to request execution id */
  public void removeExecutionId() {
    if (!executionIdLoggingEnabled()) {
      return;
    }
    for (Handler handler : rootLogger.getHandlers()) {
      if (handler instanceof JsonLogHandler) {
        ((JsonLogHandler) handler).removeExecutionId(Thread.currentThread().getId());
      }
    }
  }

  private String getOrGenerateExecutionId(HttpServletRequest request) {
    String executionId = request.getHeader(EXECUTION_ID_HTTP_HEADER);
    if (executionId == null) {
      byte[] array = new byte[EXECUTION_ID_LENGTH];
      random.nextBytes(array);
      executionId = Base64.getEncoder().encodeToString(array);
    }
    return executionId;
  }

  private boolean executionIdLoggingEnabled() {
    return Boolean.parseBoolean(System.getenv().getOrDefault(LOG_EXECUTION_ID_ENV_NAME, "false"));
  }
}
