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

package com.google.cloud.functions.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** An interface for function HTTP request. */
public interface HttpRequest {

  /** Returns body input stream. */
  InputStream getBodyInputStream() throws IOException;

  /** Returns request body reader. */
  BufferedReader getBodyReader() throws IOException;

  /** Returns request content type. */
  String getContentType();

  /** Returns request content length. */
  int getContentLength();

  /** Returns request method. */
  String getMethod();

  /** Returns request query. */
  String getQuery();

  /** Returns request parameters. */
  Map<String, List<String>> getParameters();

  /** Returns request headers. */
  Map<String, List<String>> getHeaders();

  /** Returns request parameters keys. */
  List<String> getParameterKeys();

  /** Returns request header keys. */
  List<String> getHeaderKeys();

  /** Returns request parameter for the given name. */
  List<String> getParameter(String name);

  /** Returns request header for the given name. */
  List<String> getHeader(String name);

  /** Returns request parameter value for the given name. */
  String getFirstParameter(String name);

  /** Returns request header value for the given name. */
  String getFirstHeader(String name);
}
