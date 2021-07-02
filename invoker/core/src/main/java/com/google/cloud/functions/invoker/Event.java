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

package com.google.cloud.functions.invoker;

import com.google.auto.value.AutoValue;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents an event that should be handled by a background function. This is an internal format
 * which is later converted to actual background function parameter types.
 */
@AutoValue
abstract class Event {
  static Event of(JsonElement data, CloudFunctionsContext context) {
    return new AutoValue_Event(data, context);
  }

  abstract JsonElement getData();

  abstract CloudFunctionsContext getContext();

  /** Custom deserializer that supports both GCF beta and GCF GA event formats. */
  static class EventDeserializer implements JsonDeserializer<Event> {

    @Override
    public Event deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException {
      JsonObject root = jsonElement.getAsJsonObject();

      JsonElement data = root.get("data");
      CloudFunctionsContext context;

      if (root.has("context")) {
        JsonObject contextCopy = root.getAsJsonObject("context").deepCopy();
        context =
            jsonDeserializationContext.deserialize(
                adjustContextResource(contextCopy), CloudFunctionsContext.class);
      } else if (isPubSubEmulatorPayload(root)) {
        JsonObject message = root.getAsJsonObject("message");

        String timestampString =
            message.has("publishTime")
                ? message.get("publishTime").getAsString()
                : DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now());

        context =
            CloudFunctionsContext.builder()
                .setEventType("google.pubsub.topic.publish")
                .setTimestamp(timestampString)
                .setEventId(message.get("messageId").getAsString())
                .setResource(
                    "{"
                        + "\"name\":null,"
                        + "\"service\":\"pubsub.googleapis.com\","
                        + "\"type\":\"type.googleapis.com/google.pubsub.v1.PubsubMessage\""
                        + "}")
                .build();

        JsonObject marshalledData = new JsonObject();
        marshalledData.addProperty("@type", "type.googleapis.com/google.pubsub.v1.PubsubMessage");
        marshalledData.add("data", message.get("data"));
        if (message.has("attributes")) {
          marshalledData.add("attributes", message.get("attributes"));
        }
        data = marshalledData;
      } else {
        JsonObject rootCopy = root.deepCopy();
        rootCopy.remove("data");
        context =
            jsonDeserializationContext.deserialize(
                adjustContextResource(rootCopy), CloudFunctionsContext.class);
      }
      return Event.of(data, context);
    }

    private boolean isPubSubEmulatorPayload(JsonObject root) {
      if (root.has("subscription") && root.has("message") && root.get("message").isJsonObject()) {
        JsonObject message = root.getAsJsonObject("message");
        return message.has("data") && message.has("messageId");
      }
      return false;
    }

    /**
     * Replaces 'resource' member from context JSON with its string equivalent. The original
     * 'resource' member can be a JSON object itself while {@link CloudFunctionsContext} requires it
     * to be a string.
     */
    private JsonObject adjustContextResource(JsonObject contextObject) {
      String resourceValue =
          contextObject.has("resource") ? contextObject.get("resource").toString() : "";
      contextObject.remove("resource");
      contextObject.addProperty("resource", resourceValue);
      return contextObject;
    }
  }
}
