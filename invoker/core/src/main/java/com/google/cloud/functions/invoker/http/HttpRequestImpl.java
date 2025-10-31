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

import com.google.cloud.functions.HttpRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.MultiPart.Part;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Fields;

public class HttpRequestImpl implements HttpRequest {
  private final Request request;
  private InputStream inputStream;
  private BufferedReader reader;

  public HttpRequestImpl(Request request) {
    this.request = request;
  }

  @Override
  public String getMethod() {
    return request.getMethod();
  }

  @Override
  public String getUri() {
    return request.getHttpURI().asString();
  }

  @Override
  public String getPath() {
    return request.getHttpURI().getCanonicalPath();
  }

  @Override
  public Optional<String> getQuery() {
    return Optional.ofNullable(request.getHttpURI().getQuery());
  }

  @Override
  public Map<String, List<String>> getQueryParameters() {
    Fields fields = Request.extractQueryParameters(request);
    if (fields.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, List<String>> map = new HashMap<>();
    fields.forEach(
        field -> map.put(field.getName(), Collections.unmodifiableList(field.getValues())));
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<String, HttpPart> getParts() {
    String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
    if (contentType == null
        || !contentType.startsWith(MimeTypes.Type.MULTIPART_FORM_DATA.asString())) {
      throw new IllegalStateException("Content-Type must be multipart/form-data: " + contentType);
    }

    // The multipart parsing is done by the EagerContentHandler, so we just call getParts.
    MultiPartFormData.Parts parts = MultiPartFormData.getParts(request);
    if (parts == null) {
      throw new IllegalStateException();
    }

    if (parts.size() == 0) {
      return Collections.emptyMap();
    }

    Map<String, HttpPart> map = new HashMap<>();
    parts.forEach(part -> map.put(part.getName(), new HttpPartImpl(part)));
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Optional<String> getContentType() {
    return Optional.ofNullable(request.getHeaders().get(HttpHeader.CONTENT_TYPE));
  }

  @Override
  public long getContentLength() {
    return request.getLength();
  }

  @Override
  public Optional<String> getCharacterEncoding() {
    Charset charset = Request.getCharset(request);
    return Optional.ofNullable(charset == null ? null : charset.name());
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (reader != null) {
      throw new IllegalStateException("getReader() already called");
    }
    if (inputStream == null) {
      inputStream = Content.Source.asInputStream(request);
    }
    return inputStream;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    if (reader == null) {
      if (inputStream != null) {
        throw new IllegalStateException("getInputStream already called");
      }
      inputStream = Content.Source.asInputStream(request);
      reader =
          new BufferedReader(
              new InputStreamReader(
                  getInputStream(),
                  Objects.requireNonNullElse(Request.getCharset(request), StandardCharsets.UTF_8)));
    }
    return reader;
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return HttpUtil.toStringListMap(request.getHeaders());
  }

  private static class HttpPartImpl implements HttpPart {
    private final Part part;
    private final String contentType;

    private HttpPartImpl(Part part) {
      this.part = part;
      contentType = part.getHeaders().get(HttpHeader.CONTENT_TYPE);
    }

    @Override
    public Optional<String> getFileName() {
      return Optional.ofNullable(part.getFileName());
    }

    @Override
    public Optional<String> getContentType() {
      return Optional.ofNullable(contentType);
    }

    @Override
    public long getContentLength() {
      return part.getLength();
    }

    @Override
    public Optional<String> getCharacterEncoding() {
      return Optional.ofNullable(MimeTypes.getCharsetFromContentType(contentType));
    }

    @Override
    public InputStream getInputStream() throws IOException {
      Content.Source contentSource = part.createContentSource();
      return Content.Source.asInputStream(contentSource);
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return new BufferedReader(
          new InputStreamReader(
              getInputStream(),
              Objects.requireNonNullElse(
                  MimeTypes.DEFAULTS.getCharset(contentType), StandardCharsets.UTF_8)));
    }

    @Override
    public Map<String, List<String>> getHeaders() {
      return HttpUtil.toStringListMap(part.getHeaders());
    }

    @Override
    public String toString() {
      return "%s{%s}".formatted(super.toString(), part);
    }
  }
}
