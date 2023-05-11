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

import java.lang.reflect.Type;

/**
 * Represents a Cloud Function with a strongly typed interface that is activated by an HTTP request.
 */
@FunctionalInterface
public interface TypedFunction<RequestT, ResponseT> {
  /**
   * Called to service an incoming HTTP request. This interface is implemented by user code to
   * provide the action for a given HTTP function. If this method throws any exception (including
   * any {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param arg the payload of the event, deserialized from the original JSON string.
   * @return invocation result or null to indicate the body of the response should be empty.
   * @throws Exception to produce a 500 status code in the HTTP response.
   */
  public ResponseT apply(RequestT arg) throws Exception;

  /**
   * Called to get the the format object that handles request decoding and response encoding. If
   * null is returned a default JSON format is used.
   *
   * @return the {@link WireFormat} to use for serialization
   */
  public default WireFormat getWireFormat() {
    return null;
  }

  /**
   * Describes how to deserialize request object and serialize response objects for an HTTP
   * invocation.
   */
  public interface WireFormat {
    /** Serialize is expected to encode the object to the provided HttpResponse. */
    void serialize(Object object, HttpResponse response) throws Exception;

    /**
     * Deserialize is expected to read an object of {@code Type} from the HttpRequest. The Type is
     * determined through reflection on the user's function.
     */
    Object deserialize(HttpRequest request, Type type) throws Exception;
  }
}
