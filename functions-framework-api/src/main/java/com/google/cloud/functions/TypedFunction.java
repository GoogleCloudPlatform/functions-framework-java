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
   * Called to service an incoming event. This interface is implemented by user code to provide the
   * action for a given background function. If this method throws any exception (including any
   * {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param arg the payload of the event, deserialized from the original JSON string.
   * @return invocation result or null to indicate the body of the response should be empty.
   * @throws Exception to produce a 500 status code in the HTTP response.
   */
  public ResponseT apply(RequestT arg) throws Exception;

  /**
   * Override configure to set configuration options for the function.
   *
   * @param config mutable configuration object.
   */
  public default void configure(Configuration config) {}

  /** Configures the function contract. */
  public static interface Configuration {
    /**
     * Registers a {@code WireFormat} responsible for decoding the request and encoding the
     * response. By default, the function framework provides a an implementation for JSON
     * encoding/decoding.
     *
     * @param format to use with function invocations..
     * @return this
     */
    Configuration setWireFormat(WireFormat format);
  }

  /**
   * Describes how to deserialize request object and serialize response objects for an HTTP
   * invocation.
   */
  public static interface WireFormat {
    /** Serialize is expected to encode the object to the provided HttpResponse. */
    void serialize(Object object, HttpResponse response) throws Exception;

    /**
     * Deserialize is expected to read an object of {@code Type} from the HttpRequest. The Type is
     * determined through reflection on the user's function.
     */
    Object deserialize(HttpRequest request, Type type) throws Exception;
  }
}
