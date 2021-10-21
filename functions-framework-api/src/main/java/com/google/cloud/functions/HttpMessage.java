// Copyright 2019 Google LLC
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

package com.google.cloud.functions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Represents an HTTP message, either an HTTP request or a part of a multipart HTTP request. */
public interface HttpMessage {
  /**
   * Returns the value of the {@code Content-Type} header, if any.
   *
   * @return the content type, if any.
   */
  Optional<String> getContentType();

  /**
   * Returns the numeric value of the {@code Content-Length} header.
   *
   * @return the content length.
   */
  long getContentLength();

  /**
   * Returns the character encoding specified in the {@code Content-Type} header, or {@code
   * Optional.empty()} if there is no {@code Content-Type} header or it does not have the {@code
   * charset} parameter.
   *
   * @return the character encoding for the content type, if one is specified.
   */
  Optional<String> getCharacterEncoding();

  /**
   * Returns an {@link InputStream} that can be used to read the body of this HTTP request. Every
   * call to this method on the same {@link HttpMessage} will return the same object. This method is
   * typically used to read binary data. If the body is text, the {@link #getReader()} method is
   * more appropriate.
   *
   * @return an {@link InputStream} that can be used to read the body of this HTTP request.
   * @throws IOException if a valid {@link InputStream} cannot be returned for some reason.
   * @throws IllegalStateException if {@link #getReader()} has already been called on this instance.
   */
  InputStream getInputStream() throws IOException;

  /**
   * Returns a {@link BufferedReader} that can be used to read the text body of this HTTP request.
   * Every call to this method on the same {@link HttpMessage} will return the same object.
   *
   * @return a {@link BufferedReader} that can be used to read the text body of this HTTP request.
   * @throws IOException if a valid {@link BufferedReader} cannot be returned for some reason.
   * @throws IllegalStateException if {@link #getInputStream()} has already been called on this
   *     instance.
   */
  BufferedReader getReader() throws IOException;

  /**
   * Returns a map describing the headers of this HTTP request, or this part of a multipart request.
   * If the headers look like this...
   *
   * <pre>
   *   Content-Type: text/plain
   *   Some-Header: some value
   *   Some-Header: another value
   * </pre>
   *
   * ...then the returned value will map {@code "Content-Type"} to a one-element list containing
   * {@code "text/plain"}, and {@code "Some-Header"} to a two-element list containing {@code "some
   * value"} and {@code "another value"}.
   *
   * @return a map where each key is an HTTP header and the corresponding {@code List} value has one
   *     element for each occurrence of that header.
   */
  Map<String, List<String>> getHeaders();

  /**
   * Convenience method that returns the value of the first header with the given name. If the
   * headers look like this...
   *
   * <pre>
   *   Content-Type: text/plain
   *   Some-Header: some value
   *   Some-Header: another value
   * </pre>
   *
   * ...then {@code getFirstHeader("Some-Header")} will return {@code Optional.of("some value")},
   * and {@code getFirstHeader("Another-Header")} will return {@code Optional.empty()}.
   *
   * @param name an HTTP header name.
   * @return the first value of the given header, if present.
   */
  default Optional<String> getFirstHeader(String name) {
    List<String> headers = getHeaders().get(name);
    if (headers == null || headers.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(headers.get(0));
  }
}
