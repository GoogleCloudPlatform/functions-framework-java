// Copyright 2021 Google LLC
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

import static java.util.Map.entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import io.cloudevents.CloudEvent;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/** Conversions from CloudEvents events to GCF Background Events. */
class CloudEvents {
  private static final String PUB_SUB_MESSAGE_TYPE =
      "type.googleapis.com/google.pubsub.v1.PubsubMessage";

  private static final Map<String, EventAdapter> EVENT_TYPE_MAPPING =
      Map.ofEntries(
          entry(
              "google.cloud.pubsub.topic.v1.messagePublished",
              new PubSubEventAdapter("google.pubsub.topic.publish")),
          entry(
              "google.cloud.storage.object.v1.finalized",
              new StorageEventAdapter("google.storage.object.finalize")),
          entry(
              "google.cloud.storage.object.v1.deleted",
              new StorageEventAdapter("google.storage.object.delete")),
          entry(
              "google.cloud.storage.object.v1.archived",
              new StorageEventAdapter("google.storage.object.archive")),
          entry(
              "google.cloud.storage.object.v1.metadataUpdated",
              new StorageEventAdapter("google.storage.object.metadataUpdate")),
          entry(
              "google.cloud.firestore.document.v1.written",
              new EventAdapter("providers/cloud.firestore/eventTypes/document.write")),
          entry(
              "google.cloud.firestore.document.v1.created",
              new EventAdapter("providers/cloud.firestore/eventTypes/document.create")),
          entry(
              "google.cloud.firestore.document.v1.updated",
              new EventAdapter("providers/cloud.firestore/eventTypes/document.update")),
          entry(
              "google.cloud.firestore.document.v1.deleted",
              new EventAdapter("providers/cloud.firestore/eventTypes/document.delete")),
          entry(
              "google.firebase.analytics.log.v1.written",
              new EventAdapter("providers/google.firebase.analytics/eventTypes/event.log")),
          entry(
              "google.firebase.auth.user.v1.created",
              new FirebaseAuthEventAdapter("providers/firebase.auth/eventTypes/user.create")),
          entry(
              "google.firebase.auth.user.v1.deleted",
              new FirebaseAuthEventAdapter("providers/firebase.auth/eventTypes/user.delete")),
          entry(
              "google.firebase.database.ref.v1.created",
              new FirebaseDatabaseEventAdapter(
                  "providers/google.firebase.database/eventTypes/ref.create")),
          entry(
              "google.firebase.database.ref.v1.written",
              new FirebaseDatabaseEventAdapter(
                  "providers/google.firebase.database/eventTypes/ref.write")),
          entry(
              "google.firebase.database.ref.v1.updated",
              new FirebaseDatabaseEventAdapter(
                  "providers/google.firebase.database/eventTypes/ref.update")),
          entry(
              "google.firebase.database.ref.v1.deleted",
              new FirebaseDatabaseEventAdapter(
                  "providers/google.firebase.database/eventTypes/ref.delete")),
          entry(
              "google.cloud.storage.object.v1.changed",
              new StorageEventAdapter("providers/cloud.storage/eventTypes/object.change")));

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  /**
   * Converts a CloudEvent to the legacy event format.
   *
   * @param cloudEvent the CloudEvent to convert
   * @return the legacy event representation of the Cloud Event
   */
  static Event convertToLegacyEvent(CloudEvent cloudEvent) {
    String eventType = cloudEvent.getType();
    EventAdapter eventAdapter = EVENT_TYPE_MAPPING.get(eventType);
    if (eventAdapter == null) {
      throw new IllegalArgumentException("Unrecognized CloudEvent type \"" + eventType + "\"");
    }
    return eventAdapter.convertToLegacyEvent(cloudEvent);
  }

  private static class EventAdapter {
    private final String legacyEventType;
    private static Pattern sourcePattern = Pattern.compile("//([^/]+)/(.+)");

    protected class ParsedCloudEvent {
      public final String Resource;
      public final String Service;
      public final String Name;

      public ParsedCloudEvent(String resource, String service, String name) {
        this.Resource = resource;
        this.Service = service;
        this.Name = name;
      }
    }
    ;

    /**
     * Creates an adapter to convert from the CloudEvent to a legacy event.
     *
     * @param legacyEventType the event type of the legacy event being created
     */
    EventAdapter(String legacyEventType) {
      this.legacyEventType = legacyEventType;
    }

    /**
     * Converts a CloudEvent to the legacy event format.
     *
     * @param cloudEvent the CloudEvent to convert
     * @return the legacy event representation of the Cloud Event
     */
    final Event convertToLegacyEvent(CloudEvent cloudEvent) {
      /*
        Ex 1: "//firebaseauth.googleapis.com/projects/my-project-id"
        m.group(0): "//firebaseauth.googleapis.com/projects/my-project-id"
        m.group(1): "firebaseauth.googleapis.com"
        m.group(2): "projects/my-project-id"

        Ex 2: "//pubsub.googleapis.com/projects/sample-project/topics/gcf-test"
        m.group(0): "//pubsub.googleapis.com/projects/sample-project/topics/gcf-test"
        m.group(1): "pubsub.googleapis.com"
        m.group(2): "projects/sample-project/topics/gcf-test"
      */
      Matcher m = sourcePattern.matcher(cloudEvent.getSource().toString());
      if (!m.find() || m.groupCount() != 2) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid CloudEvent source '%s', unable to parse into resource service and name",
                cloudEvent.getSource().toString()));
      }

      String service = m.group(1);
      String name = m.group(2);
      String resource = String.format("%s/%s", name, cloudEvent.getSubject());
      ParsedCloudEvent parsed = new ParsedCloudEvent(resource, service, name);

      OffsetDateTime timestamp =
          Optional.ofNullable(cloudEvent.getTime()).orElse(OffsetDateTime.now());

      CloudFunctionsContext.Builder ctxBuilder =
          CloudFunctionsContext.builder()
              .setEventId(cloudEvent.getId())
              .setEventType(this.legacyEventType)
              .setResource(resource)
              .setTimestamp(DateTimeFormatter.ISO_INSTANT.format(timestamp));

      JsonObject data =
          GSON.fromJson(
              new String(cloudEvent.getData().toBytes(), java.nio.charset.StandardCharsets.UTF_8),
              JsonObject.class);
      return createLegacyEvent(parsed, ctxBuilder, data);
    }

    /**
     * Provides a hook to furither modify the converted event for specific event adapter subclasses.
     *
     * @param event convenient information parsed from the original CloudEvent
     * @param builder the builder for the converted legacy event's context, pre-populated with
     *     defaults from the original CloudEvent
     * @param data the data for the converted legacy event's data, pre-populated with defaults from
     *     the original CloudEvent
     * @return the fully converted legacy event
     */
    Event createLegacyEvent(
        ParsedCloudEvent event, CloudFunctionsContext.Builder builder, JsonObject data) {
      return Event.of(data, builder.build());
    }
  }

  private static class PubSubEventAdapter extends EventAdapter {
    PubSubEventAdapter(String legacyEventType) {
      super(legacyEventType);
    }

    @Override
    Event createLegacyEvent(
        ParsedCloudEvent event, CloudFunctionsContext.Builder builder, JsonObject data) {
      JsonObject resource = new JsonObject();
      resource.addProperty("service", event.Service);
      resource.addProperty("name", event.Name);
      resource.addProperty("type", PUB_SUB_MESSAGE_TYPE);
      builder.setResource(GSON.toJson(resource));

      // Lift the "message" field into the main "data" field.
      if (data.has("message")) {
        JsonElement message = data.get("message");
        if (message.isJsonObject()) {
          data = message.getAsJsonObject();
        }
      }

      data.remove("messageId");
      data.remove("publishTime");

      return Event.of(data, builder.build());
    }
  }

  private static class FirebaseAuthEventAdapter extends EventAdapter {
    FirebaseAuthEventAdapter(String legacyEventType) {
      super(legacyEventType);
    }

    @Override
    Event createLegacyEvent(
        ParsedCloudEvent event, CloudFunctionsContext.Builder builder, JsonObject data) {
      builder.setResource(event.Name);

      if (data.has("metadata")) {
        JsonElement meta = data.get("metadata");
        if (meta.isJsonObject()) {
          JsonObject metaObj = meta.getAsJsonObject();

          JsonElement createTime = metaObj.get("createTime");
          if (createTime != null) {
            metaObj.add("createdAt", createTime);
            metaObj.remove("createTime");
          }

          JsonElement lastSignInTime = metaObj.get("lastSignInTime");
          if (lastSignInTime != null) {
            metaObj.add("lastSignedInAt", lastSignInTime);
            metaObj.remove("lastSignInTime");
          }
        }
      }
      return Event.of(data, builder.build());
    }
  }

  private static class FirebaseDatabaseEventAdapter extends EventAdapter {
    private static Pattern resourcePattern = Pattern.compile("/locations/[^/]+");

    FirebaseDatabaseEventAdapter(String legacyEventType) {
      super(legacyEventType);
    }

    @Override
    Event createLegacyEvent(
        ParsedCloudEvent event, CloudFunctionsContext.Builder builder, JsonObject data) {
      builder.setResource(resourcePattern.matcher(event.Resource).replaceAll(""));
      return Event.of(data, builder.build());
    }
  }

  private static class StorageEventAdapter extends EventAdapter {
    StorageEventAdapter(String legacyEventType) {
      super(legacyEventType);
    }

    @Override
    Event createLegacyEvent(
        ParsedCloudEvent event, CloudFunctionsContext.Builder builder, JsonObject data) {
      JsonObject resource = new JsonObject();
      resource.addProperty("service", event.Service);
      resource.addProperty("name", event.Resource);
      if (data.has("kind")) {
        resource.addProperty("type", data.get("kind").getAsString());
      }

      builder.setResource(GSON.toJson(resource));
      return Event.of(data, builder.build());
    }
  }
}
