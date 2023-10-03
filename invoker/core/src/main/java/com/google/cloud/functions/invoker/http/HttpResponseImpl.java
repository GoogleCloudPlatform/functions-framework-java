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

package com.google.cloud.functions.invoker.http;

import static java.util.stream.Collectors.toMap;

import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.util.IO;

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
    response.setStatus(code);
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
        .collect(
            toMap(
                name -> name,
                name -> new ArrayList<>(response.getHeaders(name)),
                (a, b) -> b,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
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
      writer = new NonBufferedWriter(response.getWriter());
    }
    return writer;
  }

  /** Close the response, flushing all content. */
  public void close() {
    try {
      IO.close(getOutputStream());
    } catch (IllegalStateException | IOException e) {
      try {
        IO.close(getWriter());
      } catch (IOException ioe) {
        // Too bad, can't close.
      }
    }
  }

  /**
   * A {@link BufferedWriter} that does not buffer. It is generally more efficient to buffer at the
   * lower level, since frequently total content is smaller than a single buffer and the lower level
   * buffer can turn a close into a last write that will avoid chunking the response if at all
   * possible. However, {@link BufferedWriter} is in the API for {@link HttpResponse}, so we must
   * return a writer of that type.
   */
  private static class NonBufferedWriter extends BufferedWriter {
    private final Writer writer;

    public NonBufferedWriter(Writer out) {
      super(out, 1);
      writer = out;
    }

    @Override
    public void write(int c) throws IOException {
      writer.write(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
      writer.write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      writer.write(cbuf, off, len);
    }

    @Override
    public void write(String str) throws IOException {
      writer.write(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
      writer.write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
      return writer.append(csq);
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
      return writer.append(csq, start, end);
    }

    @Override
    public Writer append(char c) throws IOException {
      return writer.append(c);
    }

    @Override
    public void flush() throws IOException {
      writer.flush();
    }

    @Override
    public void close() throws IOException {
      writer.close();
    }

    @Override
    public void newLine() throws IOException {
      writer.write(System.lineSeparator());
    }
  }
}
