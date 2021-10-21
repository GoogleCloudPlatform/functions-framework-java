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

/** Represents a Cloud Function that is activated by an HTTP request. */
@FunctionalInterface
public interface HttpFunction {
  /**
   * Called to service an incoming HTTP request. This interface is implemented by user code to
   * provide the action for a given function. If the method throws any exception (including any
   * {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param request a representation of the incoming HTTP request.
   * @param response an object that can be used to provide the corresponding HTTP response.
   * @throws Exception if thrown, the HTTP response will have a 500 status code.
   */
  void service(HttpRequest request, HttpResponse response) throws Exception;
}
