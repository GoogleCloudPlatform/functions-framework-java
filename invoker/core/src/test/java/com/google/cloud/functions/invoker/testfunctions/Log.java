package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Emit log messages with configurable level, message, and exception. */
public class Log implements HttpFunction {
  private static final Logger logger = Logger.getLogger(Log.class.getName());

  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    String message = request.getFirstQueryParameter("message").orElse("Default message");
    String levelString = request.getFirstQueryParameter("level").orElse("info");
    Optional<String> exceptionString = request.getFirstQueryParameter("exception");
    Field levelField = Level.class.getField(levelString.toUpperCase());
    Level level = (Level) levelField.get(null);
    if (exceptionString.isPresent()) {
      logger.log(level, message, new Exception(exceptionString.get()));
    } else {
      logger.log(level, message);
    }
  }
}
