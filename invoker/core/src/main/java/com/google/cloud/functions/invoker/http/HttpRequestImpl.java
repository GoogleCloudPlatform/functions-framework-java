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
import java.io.UncheckedIOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

public class HttpRequestImpl implements HttpRequest {
  private final HttpServletRequest request;

  public HttpRequestImpl(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public String getMethod() {
    return request.getMethod();
  }

  @Override
  public String getUri() {
    String url = request.getRequestURL().toString();
    if (request.getQueryString() != null) {
      url += "?" + request.getQueryString();
    }
    return url;
  }

  @Override
  public String getPath() {
    return request.getRequestURI();
  }

  @Override
  public Optional<String> getQuery() {
    return Optional.ofNullable(request.getQueryString());
  }

  @Override
  public Map<String, List<String>> getQueryParameters() {
    return request.getParameterMap().entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> Arrays.asList(e.getValue())));
  }

  @Override
  public Map<String, HttpPart> getParts() {
    String contentType = request.getContentType();
    if (contentType == null || !request.getContentType().startsWith("multipart/form-data")) {
      throw new IllegalStateException("Content-Type must be multipart/form-data: " + contentType);
    }
    try {
      return request.getParts().stream().collect(toMap(Part::getName, HttpPartImpl::new));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (ServletException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<String> getContentType() {
    return Optional.ofNullable(request.getContentType());
  }

  @Override
  public long getContentLength() {
    return request.getContentLength();
  }

  @Override
  public Optional<String> getCharacterEncoding() {
    return Optional.ofNullable(request.getCharacterEncoding());
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return request.getInputStream();
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return request.getReader();
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return Collections.list(request.getHeaderNames()).stream()
        .map(name -> new SimpleEntry<>(name, Collections.list(request.getHeaders(name))))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static class HttpPartImpl implements HttpPart {
    private final Part part;

    private HttpPartImpl(Part part) {
      this.part = part;
    }

    @Override
    public Optional<String> getFileName() {
      return Optional.ofNullable(part.getSubmittedFileName());
    }

    @Override
    public Optional<String> getContentType() {
      return Optional.ofNullable(part.getContentType());
    }

    @Override
    public long getContentLength() {
      return part.getSize();
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
      return part.getInputStream();
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding().orElse("utf-8");
      return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
    }

    @Override
    public Map<String, List<String>> getHeaders() {
      return part.getHeaderNames().stream()
          .map(name -> new SimpleEntry<>(name, list(part.getHeaders(name))))
          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static <T> List<T> list(Collection<T> collection) {
      return (collection instanceof List<?>) ? (List<T>) collection : new ArrayList<>(collection);
    }
  }
}
