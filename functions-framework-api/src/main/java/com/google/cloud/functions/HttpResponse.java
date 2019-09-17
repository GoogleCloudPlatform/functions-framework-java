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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the contents of an HTTP response that is being sent by a Cloud Function in response
 * to an HTTP request.
 */
public interface HttpResponse {
  /**
   * Sets the numeric HTTP
   * <a href="https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">status
   * code</a> to use in the response. Most often this will be 200, which is the OK status.
   */
  void setStatusCode(int code);

  /**
   * Sets the numeric HTTP
   * <a href="https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">status
   * code</a> and reason message to use in the response. For example<br>
   * {@code setStatusCode(400, "Something went wrong")}.
   */
  void setStatusCode(int code, String message);

  /**
   * Sets the value to use for the {@code Content-Type} header in the response. This may include
   * a character encoding, for example {@code setContentType("text/plain; charset=utf-8")}.
   */
  void setContentType(String contentType);

  /**
   * Returns the {@code Content-Type} that was previously set by {@link #setContentType}, or by
   * {@link #appendHeader} with a header name of {@code Content-Type}. If no {@code Content-Type}
   * has been set, returns {@code Optional.empty()}.
   */
  Optional<String> getContentType();

  /**
   * Includes the given header name with the given value in the response. This method may be called
   * several times for the same header, in which case the response will contain the header the same
   * number of times.
   */
  void appendHeader(String header, String value);

  /**
   * Returns the headers that have been defined for the response so far. This will contain at least
   * the headers that have been set via {@link #appendHeader} or {@link #setContentType}, and may
   * contain additional headers such as {@code Date}.
   */
  Map<String, List<String>> getHeaders();

  /**
   * Returns an {@link OutputStream} that can be used to write the body of the response.
   * This method is typically used to write binary data. If the body is text, the
   * {@link #getWriter()} method is more appropriate.
   *
   * @throws IOException if a valid {@link OutputStream} cannot be returned for some reason.
   * @throws IllegalStateException if {@link #getWriter} has already been called on this instance.
   */
  OutputStream getOutputStream() throws IOException;

  /**
   * Returns a {@link BufferedWriter} that can be used to write the text body of the response.
   * If the written text will not be US-ASCII, you should specify a character encoding by calling
   * {@link #setContentType setContentType("text/foo; charset=bar")} or
   * {@link #appendHeader appendHeader("Content-Type", "text/foo; charset=bar")}
   * before calling this method.
   *
   * @throws IOException if a valid {@link BufferedWriter} cannot be returned for some reason.
   * @throws IllegalStateException if {@link #getOutputStream} has already been called on this
   *     instance.
   */
  BufferedWriter getWriter() throws IOException;
}
