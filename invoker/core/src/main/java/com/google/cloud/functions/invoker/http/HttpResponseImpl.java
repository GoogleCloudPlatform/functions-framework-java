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

import com.google.cloud.functions.HttpResponse;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.WriteThroughWriter;
import org.eclipse.jetty.server.Response;

public class HttpResponseImpl implements HttpResponse {
  private final Response response;
  private OutputStream outputStream;
  private BufferedWriter writer;

  public HttpResponseImpl(Response response) {
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
    response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType);
  }

  @Override
  public Optional<String> getContentType() {
    return Optional.ofNullable(response.getHeaders().get(HttpHeader.CONTENT_TYPE));
  }

  @Override
  public void appendHeader(String key, String value) {
    response.getHeaders().add(key, value);
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return HttpRequestImpl.toStringListMap(response.getHeaders());
  }

  @Override
  public OutputStream getOutputStream() {
    if (writer != null) {
      throw new IllegalStateException("getWriter called");
    } else if (outputStream == null) {
      // TODO use BufferedSink when it is available
      outputStream = new BufferedOutputStream(Content.Sink.asOutputStream(response));
    }
    return outputStream;
  }

  @Override
  public synchronized BufferedWriter getWriter() throws IOException {
    if (writer == null) {
      if (outputStream != null) {
        throw new IllegalStateException("getOutputStream called");
      }
      String contentType = getContentType().orElse(null);
      Charset charset = Objects.requireNonNullElse(
          response.getRequest().getContext().getMimeTypes().getCharset(contentType),
          StandardCharsets.UTF_8);
      // TODO should we buffer in the input stream rather than as characters
      outputStream = Content.Sink.asOutputStream(response);
      writer = new BufferedWriter(WriteThroughWriter.newWriter(getOutputStream(), charset));
    }
    return writer;
  }

  public void close() {
    try {
      if (writer != null) {
        writer.close();
      } else if (outputStream != null) {
        outputStream.close();
      }
    } catch (IOException e) {
      // Too bad, can't close.
    }
  }
}
