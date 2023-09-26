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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.WriteThroughWriter;
import org.eclipse.jetty.io.content.BufferedContentSink;
import org.eclipse.jetty.io.content.ContentSinkOutputStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class HttpResponseImpl implements HttpResponse {
  private final Response response;
  private ContentSinkOutputStream contentSinkOutputStream;
  private BufferedWriter writer;
  private Charset charset;

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
    charset = response.getRequest().getContext().getMimeTypes().getCharset(contentType);
  }

  @Override
  public Optional<String> getContentType() {
    return Optional.ofNullable(response.getHeaders().get(HttpHeader.CONTENT_TYPE));
  }

  @Override
  public void appendHeader(String key, String value) {
    if (HttpHeader.CONTENT_TYPE.is(key)) {
      setContentType(value);
    } else {
      response.getHeaders().add(key, value);
    }
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return HttpRequestImpl.toStringListMap(response.getHeaders());
  }

  @Override
  public OutputStream getOutputStream() {
    if (writer != null) {
      throw new IllegalStateException("getWriter called");
    } else if (contentSinkOutputStream == null) {
      Request request = response.getRequest();
      int outputBufferSize = request.getConnectionMetaData().getHttpConfiguration()
          .getOutputBufferSize();
      BufferedContentSink bufferedContentSink = new BufferedContentSink(response,
          request.getComponents().getByteBufferPool(),
          false, outputBufferSize / 2, outputBufferSize);
      contentSinkOutputStream = new ContentSinkOutputStream(bufferedContentSink);
    }
    return contentSinkOutputStream;
  }

  @Override
  public synchronized BufferedWriter getWriter() throws IOException {
    if (writer == null) {
      if (contentSinkOutputStream != null) {
        throw new IllegalStateException("getOutputStream called");
      }

      writer = new NonBufferedWriter(WriteThroughWriter.newWriter(getOutputStream(),
          Objects.requireNonNullElse(charset, StandardCharsets.UTF_8)));
    }
    return writer;
  }

  /**
   * Close the response, flushing all content.
   *
   * @param callback a {@link Callback} to be completed when the response is closed.
   */
  public void close(Callback callback) {
    try {
      if (writer != null) {
        writer.flush();
      }
      if (contentSinkOutputStream != null) {
        // Do an asynchronous close, so large buffered content may be written without blocking
        contentSinkOutputStream.close(callback);
      } else {
        callback.succeeded();
      }
    } catch (IOException e) {
      // Too bad, can't close.
    }
  }

  /**
   * A {@link BufferedWriter} that does not buffer.
   * It is generally more efficient to buffer at the {@link Content.Sink} level,
   * since frequently total content is smaller than a single buffer and
   * the {@link Content.Sink} can turn a close into a last write that will avoid
   * chunking the response if at all possible.   However, {@link BufferedWriter}
   * is in the API for {@link HttpResponse}, so we must return a writer of
   * that type.
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
