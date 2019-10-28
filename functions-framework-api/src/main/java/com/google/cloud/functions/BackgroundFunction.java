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

import com.google.gson.JsonElement;

/**
 * Represents a Cloud Function that is activated by an event.
 *
 * <p>Here is an example of an implementation that operates on the JSON payload of the event
 * directly:
 *
 * <pre>
 * public class Example implements BackgroundFunction {
 *   private static final Logger logger = Logger.getLogger(Example.class.getName());
 *
 *   {@code @Override}
 *   public void accept(JsonElement json, Context context) {
 *     JsonElement messageId = json.getAsJsonObject().get("messageId");
 *     String messageIdString = messageId.getAsJsonString();
 *     logger.info("Got messageId " + messageIdString);
 *   }
 * }
 * </pre>
 *
 * <p>Here is an example of an implementation that deserializes the JSON payload into a Java
 * object for simpler access:
 *
 * <pre>
 * public class Example implements BackgroundFunction {
 *   private static final Logger logger = Logger.getLogger(Example.class.getName());
 *
 *   {@code @Override}
 *   public void accept(JsonElement json, Context context) {
 *     PubSubMessage message = Gson.fromJson(json, PubSubMessage.class);
 *     logger.info("Got messageId " + message.messageId);
 *   }
 * }
 *
 * // Where PubSubMessage is a user-defined class like this:
 * public class PubSubMessage {
 *   String data;
 *   {@code Map<String, String>} attributes;
 *   String messageId;
 *   String publishTime;
 * }
 * </pre>
 */
@FunctionalInterface
public interface BackgroundFunction {
  /**
   * Called to service an incoming event. This interface is implemented by user code to
   * provide the action for a given background function.
   * (including any {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param json the payload of the event, as a parsed JSON object.
   * @param context the context of the event. This is a set of values that every event has,
   *     separately from the payload, such as timestamp and event type.
   */
  public void accept(JsonElement json, Context context);
}
