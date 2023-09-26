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

import com.google.cloud.functions.HttpRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPart.Part;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Fields.Field;

public class HttpRequestImpl implements HttpRequest {
  private final Request request;

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

    return Request.extractQueryParameters(request).stream()
        .collect(toMap(Field::getName, Field::getValues));
  }

  @Override
  public Map<String, HttpPart> getParts() {
    // TODO initiate reading the parts asynchronously before invocation
    String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
    if (contentType == null || !contentType.startsWith("multipart/form-data")) {
      throw new IllegalStateException("Content-Type must be multipart/form-data: " + contentType);
    }
    String boundary = MultiPart.extractBoundary(contentType);
    if (boundary == null) {
      throw new IllegalStateException("No boundary in content-type: " + contentType);
    }
    try {
      MultiPartFormData.Parts parts =
          MultiPartFormData.from(request, boundary, parser -> {
            parser.setMaxMemoryFileSize(-1);
            return parser.parse(request);
          }).get();
      return StreamSupport.stream(parts.spliterator(), false)
          .map(HttpPartImpl::new)
          .collect(toMap(HttpPartImpl::getName, p -> p));
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
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

  private InputStream inputStream;
  private BufferedReader reader;

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
      reader = new BufferedReader(new InputStreamReader(getInputStream(),
          getCharacterEncoding().orElse(StandardCharsets.UTF_8.name())));
    }
    return reader;
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return toStringListMap(request.getHeaders());
  }

  static Map<String, List<String>> toStringListMap(HttpFields headers) {
    Map<String, List<String>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (HttpField field : headers) {
      map.computeIfAbsent(field.getName(), key -> new ArrayList<>()).add(field.getValue());
    }
    return map;
  }

  private static class HttpPartImpl implements HttpPart {
    private final Part part;

    private HttpPartImpl(Part part) {
      this.part = part;
    }

    public String getName() {
      return part.getName();
    }

    @Override
    public Optional<String> getFileName() {
      return Optional.ofNullable(part.getFileName());
    }

    @Override
    public Optional<String> getContentType() {
      return Optional.ofNullable(part.getHeaders().get(HttpHeader.CONTENT_TYPE));
    }

    @Override
    public long getContentLength() {
      return part.getLength();
    }

    @Override
    public Optional<String> getCharacterEncoding() {
      String contentType = getContentType().orElse(null);
      if (contentType == null) {
        return Optional.empty();
      }
      Pattern charsetPattern = Pattern.compile("(?i).*;\\s*charset\\s*=([^;\\s]*)\\s*(;|$)");
      Matcher matcher = charsetPattern.matcher(contentType);
      return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return Content.Source.asInputStream(part.newContentSource());
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding().orElse("utf-8");
      return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
    }

    @Override
    public Map<String, List<String>> getHeaders() {
      return HttpRequestImpl.toStringListMap(part.getHeaders());
    }

    private static <T> List<T> list(Collection<T> collection) {
      return (collection instanceof List<?>) ? (List<T>) collection : new ArrayList<>(collection);
    }

    @Override
    public String toString() {
      return "%s{%s}".formatted(super.toString(), part);
    }
  }
}
