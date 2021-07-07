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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacyEventAdapter {

  private static final Pattern CLOUD_EVENT_SOURCE_PATTERN = Pattern.compile("^//([^/]+)/(.+)$");
  private static final Map<String, String> EVENT_TYPE_MAPPING =
      Map.ofEntries(
          entry("google.cloud.pubsub.topic.v1.messagePublished", "google.pubsub.topic.publish"),
          entry("google.cloud.storage.object.v1.finalized", "google.storage.object.finalize"),
          entry("google.cloud.storage.object.v1.deleted", "google.storage.object.delete"),
          entry("google.cloud.storage.object.v1.archived", "google.storage.object.archive"),
          entry(
              "google.cloud.storage.object.v1.metadataUpdated",
              "google.storage.object.metadataUpdate"),
          entry(
              "google.cloud.firestore.document.v1.written",
              "providers/cloud.firestore/eventTypes/document.write"),
          entry(
              "google.cloud.firestore.document.v1.created",
              "providers/cloud.firestore/eventTypes/document.create"),
          entry(
              "google.cloud.firestore.document.v1.updated",
              "providers/cloud.firestore/eventTypes/document.update"),
          entry(
              "google.cloud.firestore.document.v1.deleted",
              "providers/cloud.firestore/eventTypes/document.delete"),
          entry(
              "google.firebase.auth.user.v1.created",
              "providers/firebase.auth/eventTypes/user.create"),
          entry(
              "google.firebase.auth.user.v1.deleted",
              "providers/firebase.auth/eventTypes/user.delete"),
          entry(
              "google.firebase.analytics.log.v1.written",
              "providers/google.firebase.analytics/eventTypes/event.log"),
          entry(
              "google.firebase.database.document.v1.created",
              "providers/google.firebase.database/eventTypes/ref.create"),
          entry(
              "google.firebase.database.document.v1.written",
              "providers/google.firebase.database/eventTypes/ref.write"),
          entry(
              "google.firebase.database.document.v1.updated",
              "providers/google.firebase.database/eventTypes/ref.update"),
          entry(
              "google.firebase.database.document.v1.deleted",
              "providers/google.firebase.database/eventTypes/ref.delete"));

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  static Event convertToLegacyEvent(CloudEvent cloudEvent) {
    if (cloudEvent.getData() == null) {
      throw new IllegalStateException("Event has no \"data\" component");
    }
    String ceType = cloudEvent.getType();
    if (!EVENT_TYPE_MAPPING.containsKey(ceType)) {
      throw new IllegalStateException(
          "Unable to find background event equivalent type for: " + ceType);
    }

    Matcher matcher = CLOUD_EVENT_SOURCE_PATTERN.matcher(cloudEvent.getSource().toString());
    if (!matcher.matches()) {
      throw new IllegalStateException("Unexpected CloudEvent source.");
    }
    String service = matcher.group(1);
    String name = matcher.group(2);

    String rawData = new String(cloudEvent.getData().toBytes(), UTF_8);
    JsonElement data = GSON.fromJson(rawData, JsonElement.class);
    String resource;
    switch (service) {
      case "pubsub.googleapis.com":
        resource =
            jsonResource(service, name, "type.googleapis.com/google.pubsub.v1.PubsubMessage");
        if (data.isJsonObject() && data.getAsJsonObject().has("message")) {
          data = data.getAsJsonObject().get("message");
        }
        if (data.isJsonObject() && data.getAsJsonObject().has("messageId")) {
          data.getAsJsonObject().remove("messageId");
        }
        if (data.isJsonObject() && data.getAsJsonObject().has("publishTime")) {
          data.getAsJsonObject().remove("publishTime");
        }
        break;
      case "firebaseauth.googleapis.com":
        resource = "\"" + name + "\"";
        if (data.isJsonObject() && data.getAsJsonObject().has("metadata")) {
          JsonObject metadata = data.getAsJsonObject().getAsJsonObject("metadata");
          if (metadata.has("createTime")) {
            metadata.add("createdAt", metadata.get("createTime"));
            metadata.remove("createTime");
          }
          if (metadata.has("lastSignInTime")) {
            metadata.add("lastSignedInAt", metadata.get("lastSignInTime"));
            metadata.remove("lastSignInTime");
          }
        }
        break;

      case "storage.googleapis.com":
        String kind = data.getAsJsonObject().get("kind").getAsString();
        name = String.format("%s/%s", name, cloudEvent.getSubject());
        resource = jsonResource(service, name, kind);
        break;

      case "firebasedatabase.googleapis.com":
        name = name.replaceAll("locations/[^/]+/", "");
        resource = String.format("\"%s/%s\"", name, cloudEvent.getSubject());
        break;

      default:
        resource = String.format("\"%s/%s\"", name, cloudEvent.getSubject());
    }
    OffsetDateTime timestamp =
        Optional.ofNullable(cloudEvent.getTime()).orElse(OffsetDateTime.now());
    String timestampString = DateTimeFormatter.ISO_INSTANT.format(timestamp);
    // We don't have an obvious replacement for the Context.resource field, which with legacy events
    // corresponded to a value present for some proprietary Google event types.
    // Map<String, String> attributesMap =
    //     cloudEvent.getAttributeNames().stream()
    //         .collect(toMap(a -> a, a -> String.valueOf(cloudEvent.getAttribute(a))));
    CloudFunctionsContext context =
        CloudFunctionsContext.builder()
            .setEventId(cloudEvent.getId())
            .setEventType(EVENT_TYPE_MAPPING.get(cloudEvent.getType()))
            .setResource(resource)
            .setTimestamp(timestampString)
            // .setAttributes(attributesMap)
            .build();

    return Event.of(data, context);
  }

  private static String jsonResource(String service, String name, String type) {
    return String.format(
        "{\"service\":\"%s\",\"name\":\"%s\",\"type\": \"%s\"}", service, name, type);
  }
}
