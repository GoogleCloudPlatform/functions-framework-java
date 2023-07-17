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

/**
 * Represents a Cloud Function that is activated by an event. The payload of the event is a JSON
 * object, which can be parsed using a JSON package such as <a
 * href="https://github.com/google/gson/blob/main/UserGuide.md">GSON</a>.
 *
 * <p>Here is an example of an implementation that parses the JSON payload using Gson, to access its
 * {@code messageId} property:
 * <!-- The {@code} placement is a bit strange here, to prevent spurious spaces introduced by the
 *      javadoc tool. -->
 *
 * <pre>
 * public class Example implements RawBackgroundFunction {
 *   private static final Logger logger = Logger.getLogger(Example.class.getName());
 *
 *  {@code @Override}
 *   public void accept(String json, Context context) {
 *     JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
 *     JsonElement messageId = jsonObject.get("messageId");
 *     String messageIdString = messageId.getAsJsonString();
 *     logger.info("Got messageId " + messageIdString);
 *   }
 * }
 * </pre>
 *
 * <p>Here is an example of an implementation that deserializes the JSON payload into a Java object
 * for simpler access, again using Gson:
 *
 * <pre>
 * public class Example implements RawBackgroundFunction {
 *   private static final Logger logger = Logger.getLogger(Example.class.getName());
 *
 *  {@code @Override}
 *   public void accept(String json, Context context) {
 *     PubSubMessage message = new Gson().fromJson(json, PubSubMessage.class);
 *     logger.info("Got messageId " + message.messageId);
 *   }
 * }
 *
 * // Where PubSubMessage is a user-defined class like this:
 * public class PubSubMessage {
 *   String data;
 *  {@code Map<String, String>} attributes;
 *   String messageId;
 *   String publishTime;
 * }
 * </pre>
 */
@FunctionalInterface
public interface RawBackgroundFunction {
  /**
   * Called to service an incoming event. This interface is implemented by user code to provide the
   * action for a given background function. If this method throws any exception (including any
   * {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param json the payload of the event, as a JSON string.
   * @param context the context of the event. This is a set of values that every event has,
   *     separately from the payload, such as timestamp and event type.
   * @throws Exception to produce a 500 status code in the HTTP response.
   */
  void accept(String json, Context context) throws Exception;
}
