package com.google.cloud.functions.invoker.gcf;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A log handler that publishes log messages in a json format. This is StackDriver's <a
 * href="https://cloud.google.com/logging/docs/structured-logging">"structured logging" format</a>.
 */
public final class JsonLogHandler extends Handler {
  private static final String SOURCE_LOCATION_KEY = "\"logging.googleapis.com/sourceLocation\": ";
  private static final String LOG_EXECUTION_ID_ENV_NAME = "LOG_EXECUTION_ID";

  private static final String DEBUG = "DEBUG";
  private static final String INFO = "INFO";
  private static final String WARNING = "WARNING";
  private static final String ERROR = "ERROR";
  private static final String DEFAULT = "DEFAULT";

  private final PrintStream out;
  private final boolean closePrintStreamOnClose;
  // This map is used to track execution id for currently running Jetty requests. Mapping thread
  // id to request works because of an implementation detail of Jetty thread pool handling.
  // Jetty worker threads completely handle a request before beginning work on a new request.
  // NOTE: Store thread id as a string to avoid comparison failures between int and long.
  //
  // Jetty Documentation (https://jetty.org/docs/jetty/10/programming-guide/arch/threads.html)
  private static final ConcurrentMap<String, String> executionIdByThreadMap =
      new ConcurrentHashMap<>();

  public JsonLogHandler(PrintStream out, boolean closePrintStreamOnClose) {
    this.out = out;
    this.closePrintStreamOnClose = closePrintStreamOnClose;
  }

  @Override
  public void publish(LogRecord record) {
    // We avoid String.format and String.join even though they would simplify the code.
    // Logging code often shows up in profiling so we want to make this fast and StringBuilder is
    // more performant.
    StringBuilder json = new StringBuilder("{");
    appendSeverity(json, record);
    appendSourceLocation(json, record);
    appendExecutionId(json, record);
    appendMessage(json, record); // must be last, see appendMessage
    json.append("}");
    // We must output the log all at once (should only call println once per call to publish)
    out.println(json);
  }

  private static void appendMessage(StringBuilder json, LogRecord record) {
    // This must be the last item in the JSON object, because it has no trailing comma. JSON is
    // unforgiving about commas and you can't have one just before }.
    json.append("\"message\": \"").append(escapeString(record.getMessage()));
    if (record.getThrown() != null) {
      json.append("\\n").append(escapeString(getStackTraceAsString(record.getThrown())));
    }
    json.append("\"");
  }

  private static void appendSeverity(StringBuilder json, LogRecord record) {
    json.append("\"severity\": \"").append(levelToSeverity(record.getLevel())).append("\", ");
  }

  private static String levelToSeverity(Level level) {
    int intLevel = (level == null) ? 0 : level.intValue();
    switch (intLevel) {
      case 300: // FINEST
      case 400: // FINER
      case 500: // FINE
        return DEBUG;
      case 700: // CONFIG
      case 800: // INFO
        // Java's CONFIG is lower than its INFO, while Stackdriver's NOTICE is greater than its
        // INFO. So despite the similarity, we don't try to use NOTICE for CONFIG.
        return INFO;
      case 900: // WARNING
        return WARNING;
      case 1000: // SEVERE
        return ERROR;
      default:
        return DEFAULT;
    }
  }

  private static void appendSourceLocation(StringBuilder json, LogRecord record) {
    if (record.getSourceClassName() == null && record.getSourceMethodName() == null) {
      return;
    }
    List<String> entries = new ArrayList<>();
    if (record.getSourceClassName() != null) {
      // TODO: Handle nested classes. If the source class name is com.example.Foo$Bar then the
      // source file is com/example/Foo.java, not com/example/Foo$Bar.java.
      String fileName = record.getSourceClassName().replace('.', '/') + ".java";
      entries.add("\"file\": \"" + escapeString(fileName) + "\"");
    }
    if (record.getSourceMethodName() != null) {
      entries.add("\"method\": \"" + escapeString(record.getSourceMethodName()) + "\"");
    }
    json.append(SOURCE_LOCATION_KEY).append("{").append(String.join(", ", entries)).append("}, ");
  }

  private void appendExecutionId(StringBuilder json, LogRecord record) {
    if (executionIdLoggingEnabled()) {
      json.append("\"execution_id\": \"")
          .append(executionIdByThreadMap.get(Integer.toString(record.getThreadID())))
          .append("\", ");
    }
  }

  private static String escapeString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  private static String getStackTraceAsString(Throwable t) {
    StringWriter stringWriter = new StringWriter();
    t.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }

  @Override
  public void flush() {
    out.flush();
  }

  @Override
  public void close() throws SecurityException {
    if (closePrintStreamOnClose) {
      out.close();
    }
  }

  public void addExecutionId(long threadId, String executionId) {
    executionIdByThreadMap.put(Long.toString(threadId), executionId);
  }

  public void removeExecutionId(long threadId) {
    executionIdByThreadMap.remove(Long.toString(threadId));
  }

  private boolean executionIdLoggingEnabled() {
    return Boolean.parseBoolean(System.getenv().getOrDefault(LOG_EXECUTION_ID_ENV_NAME, "false"));
  }
}
