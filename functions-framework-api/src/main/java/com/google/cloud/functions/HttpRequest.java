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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Represents the contents of an HTTP request that is being serviced by a Cloud Function. */
public interface HttpRequest extends HttpMessage {
  /**
   * The HTTP method of this request, such as {@code "POST"} or {@code "GET"}.
   *
   * @return the HTTP method of this request.
   */
  String getMethod();

  /**
   * The full URI of this request as it arrived at the server.
   *
   * @return the full URI of this request.
   */
  String getUri();

  /**
   * The path part of the URI for this request, without any query. If the full URI is {@code
   * http://foo.com/bar/baz?this=that}, then this method will return {@code /bar/baz}.
   *
   * @return the path part of the URI for this request.
   */
  String getPath();

  /**
   * The query part of the URI for this request. If the full URI is {@code
   * http://foo.com/bar/baz?this=that}, then this method will return {@code this=that}. If there is
   * no query part, the returned {@code Optional} is empty.
   *
   * @return the query part of the URI, if any.
   */
  Optional<String> getQuery();

  /**
   * The query parameters of this request. If the full URI is {@code
   * http://foo.com/bar?thing=thing1&thing=thing2&cat=hat}, then the returned map will map {@code
   * thing} to the list {@code ["thing1", "thing2"]} and {@code cat} to the list with the single
   * element {@code "hat"}.
   *
   * @return a map where each key is the name of a query parameter and the corresponding {@code
   *     List} value indicates every value that was associated with that name.
   */
  Map<String, List<String>> getQueryParameters();

  /**
   * The first query parameter with the given name, if any. If the full URI is {@code
   * http://foo.com/bar?thing=thing1&thing=thing2&cat=hat}, then {@code
   * getFirstQueryParameter("thing")} will return {@code Optional.of("thing1")} and {@code
   * getFirstQueryParameter("something")} will return {@code Optional.empty()}. This is a more
   * convenient alternative to {@link #getQueryParameters}.
   *
   * @param name a query parameter name.
   * @return the first query parameter value with the given name, if any.
   */
  default Optional<String> getFirstQueryParameter(String name) {
    List<String> parameters = getQueryParameters().get(name);
    if (parameters == null || parameters.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parameters.get(0));
  }

  /**
   * Represents one part inside a multipart ({@code multipart/form-data}) HTTP request. Each such
   * part can have its own HTTP headers, which can be retrieved with the methods inherited from
   * {@link HttpMessage}.
   */
  interface HttpPart extends HttpMessage {
    /**
     * Returns the filename associated with this part, if any.
     *
     * @return the filename associated with this part, if any.
     */
    Optional<String> getFileName();
  }

  /**
   * Returns the parts inside this multipart ({@code multipart/form-data}) HTTP request. Each entry
   * in the returned map has the name of the part as its key and the contents as the associated
   * value.
   *
   * @return a map from part names to part contents.
   * @throws IllegalStateException if the {@link #getContentType() content type} is not {@code
   *     multipart/form-data}.
   */
  Map<String, HttpPart> getParts();
}
