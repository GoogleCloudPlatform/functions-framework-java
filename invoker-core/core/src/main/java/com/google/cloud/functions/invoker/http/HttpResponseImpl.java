package com.google.cloud.functions.invoker.http;

import static java.util.stream.Collectors.toMap;

import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;

public class HttpResponseImpl implements HttpResponse {
  private final HttpServletResponse response;

  public HttpResponseImpl(HttpServletResponse response) {
    this.response = response;
  }

  @Override
  public void setStatusCode(int code) {
    response.setStatus(code);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setStatusCode(int code, String message) {
    response.setStatus(code, message);
  }

  @Override
  public void setContentType(String contentType) {
    response.setContentType(contentType);
  }

  @Override
  public Optional<String> getContentType() {
    return Optional.ofNullable(response.getContentType());
  }

  @Override
  public void appendHeader(String key, String value) {
    response.addHeader(key, value);
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return response.getHeaderNames().stream()
        .map(header -> new SimpleEntry<>(header, list(response.getHeaders(header))))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static <T> List<T> list(Collection<T> collection) {
    return (collection instanceof List<?>) ? (List<T>) collection : new ArrayList<>(collection);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return response.getOutputStream();
  }

  private BufferedWriter writer;

  @Override
  public synchronized BufferedWriter getWriter() throws IOException {
    if (writer != null) {
      return writer;
    }
    // We could just wrap a BufferedWriter around the PrintWriter that the Servlet API gives us,
    // but this slightly clunky alternative potentially avoids two intermediate objects in the
    // writer chain.
    PrintWriter printWriter = response.getWriter();
    Writer wrappedWriter;
    try {
      // This is a protected field, so it is part of the documented API and we know it will be
      // there, but we need to use reflection to get at it.
      Field outField = PrintWriter.class.getDeclaredField("out");
      outField.setAccessible(true);
      wrappedWriter = (Writer) outField.get(printWriter);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Reflection failed", e);
    }
    this.writer = (wrappedWriter instanceof BufferedWriter)
        ? (BufferedWriter) wrappedWriter
        : new BufferedWriter(wrappedWriter);
    return this.writer;
  }
}
