package com.google.cloud.functions.invoker.http;

import static java.util.stream.Collectors.toMap;

import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
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
    if (writer == null) {
      // Unfortunately this means that we get two intermediate objects between the object we return
      // and the underlying Writer that response.getWriter() wraps. We could try accessing the
      // PrintWriter.out field via reflection, but that sort of access to non-public fields of
      // platform classes is now frowned on and may draw warnings or even fail in subsequent
      // versions.
      // We could instead wrap the OutputStream, but that would require us to deduce the appropriate
      // Charset, using logic like this:
      // https://github.com/eclipse/jetty.project/blob/923ec38adf/jetty-server/src/main/java/org/eclipse/jetty/server/Response.java#L731
      // We may end up doing that if performance is an issue.
      writer = new BufferedWriter(response.getWriter());
    }
    return writer;
  }
}
