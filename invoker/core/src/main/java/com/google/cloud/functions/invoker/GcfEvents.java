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

import com.google.auto.value.AutoValue;
import com.google.cloud.functions.invoker.CloudFunctionsContext.Nullable;
import com.google.cloud.functions.invoker.CloudFunctionsContext.Resource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversions from GCF events to CloudEvents.
 */
class GcfEvents {
  private static final String FIREBASE_SERVICE = "firebase.googleapis.com";
  private static final String FIREBASE_AUTH_SERVICE = "firebaseauth.googleapis.com";
  private static final String FIREBASE_DB_SERVICE = "firebasedatabase.googleapis.com";
  private static final String FIRESTORE_SERVICE = "firestore.googleapis.com";
  private static final String PUB_SUB_SERVICE = "pubsub.googleapis.com";
  private static final String STORAGE_SERVICE = "storage.googleapis.com";

  private static final String PUB_SUB_MESSAGE_PUBLISHED = "google.cloud.pubsub.topic.v1.messagePublished";

  private static final Map<String, EventAdapter> EVENT_TYPE_MAPPING = Map.ofEntries(
      entry("google.pubsub.topic.publish", new PubSubEventAdapter(PUB_SUB_MESSAGE_PUBLISHED)),

      entry("google.storage.object.finalize",
          new StorageEventAdapter("google.cloud.storage.object.v1.finalized")),
      entry("google.storage.object.delete",
          new StorageEventAdapter("google.cloud.storage.object.v1.deleted")),
      entry("google.storage.object.archive",
          new StorageEventAdapter("google.cloud.storage.object.v1.archived")),
      entry("google.storage.object.metadataUpdate",
          new StorageEventAdapter("google.cloud.storage.object.v1.metadataUpdated")),

      entry("providers/cloud.firestore/eventTypes/document.write",
          new FirestoreFirebaseEventAdapter("google.cloud.firestore.document.v1.written",
              FIRESTORE_SERVICE)),
      entry("providers/cloud.firestore/eventTypes/document.create",
          new FirestoreFirebaseEventAdapter("google.cloud.firestore.document.v1.created",
              FIRESTORE_SERVICE)),
      entry("providers/cloud.firestore/eventTypes/document.update",
          new FirestoreFirebaseEventAdapter("google.cloud.firestore.document.v1.updated",
              FIRESTORE_SERVICE)),
      entry("providers/cloud.firestore/eventTypes/document.delete",
          new FirestoreFirebaseEventAdapter("google.cloud.firestore.document.v1.deleted",
              FIRESTORE_SERVICE)),

      entry("providers/firebase.auth/eventTypes/user.create",
          new FirebaseAuthEventAdapter("google.firebase.auth.user.v1.created")),
      entry("providers/firebase.auth/eventTypes/user.delete",
          new FirebaseAuthEventAdapter("google.firebase.auth.user.v1.deleted")),

      entry("providers/google.firebase.analytics/eventTypes/event.log",
          new FirestoreFirebaseEventAdapter("google.firebase.analytics.log.v1.written", FIREBASE_SERVICE)),

      entry("providers/google.firebase.database/eventTypes/ref.create",
          new FirestoreFirebaseEventAdapter("google.firebase.database.document.v1.created",
              FIREBASE_DB_SERVICE)),
      entry("providers/google.firebase.database/eventTypes/ref.write",
          new FirestoreFirebaseEventAdapter("google.firebase.database.document.v1.written",
              FIREBASE_DB_SERVICE)),
      entry("providers/google.firebase.database/eventTypes/ref.update",
          new FirestoreFirebaseEventAdapter("google.firebase.database.document.v1.updated",
              FIREBASE_DB_SERVICE)),
      entry("providers/google.firebase.database/eventTypes/ref.delete",
          new FirestoreFirebaseEventAdapter("google.firebase.database.document.v1.deleted",
              FIREBASE_DB_SERVICE)),

      entry("providers/cloud.pubsub/eventTypes/topic.publish",
          new PubSubEventAdapter(PUB_SUB_MESSAGE_PUBLISHED)),

      entry("providers/cloud.storage/eventTypes/object.change",
          new StorageEventAdapter("google.cloud.storage.object.v1.changed"))
  );

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  static CloudEvent convertToCloudEvent(Event legacyEvent) {
    String eventType = legacyEvent.getContext().eventType();
    EventAdapter eventAdapter = EVENT_TYPE_MAPPING.get(eventType);
    if (eventAdapter == null) {
      throw new IllegalArgumentException("Unrecognized event type \"" + eventType + "\"");
    }
    return eventAdapter.convertToCloudEvent(legacyEvent);
  }

  @AutoValue
  abstract static class SourceAndSubject {
    /** The source URI, without the initial {@code //<service>/}. */
    abstract String source();
    abstract @Nullable String subject();

    static SourceAndSubject of(String source, String subject) {
      return new AutoValue_GcfEvents_SourceAndSubject(source, subject);
    }
  }

  private abstract static class EventAdapter {
    private final String cloudEventType;
    private final String defaultService;

    EventAdapter(String cloudEventType, String defaultService) {
      this.cloudEventType = cloudEventType;
      this.defaultService = defaultService;
    }

    final CloudEvent convertToCloudEvent(Event legacyEvent) {
      String jsonData = GSON.toJson(legacyEvent.getData());
      jsonData = maybeReshapeData(legacyEvent, jsonData);
      Resource resource = Resource.from(legacyEvent.getContext().resource());
      String service = Optional.ofNullable(resource.service()).orElse(defaultService);
      String resourceName = resource.name();
      SourceAndSubject sourceAndSubject = convertResourceToSourceAndSubject(resourceName, legacyEvent);
      URI source = URI.create("//" + service + "/" + sourceAndSubject.source());
      OffsetDateTime timestamp =
          Optional.ofNullable(legacyEvent.getContext().timestamp())
              .map(s -> OffsetDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME))
              .orElse(null);
      return CloudEventBuilder.v1()
          .withData(jsonData.getBytes(UTF_8))
          .withDataContentType("application/json")
          .withId(legacyEvent.getContext().eventId())
          .withSource(source)
          .withSubject(sourceAndSubject.subject())
          .withTime(timestamp)
          .withType(cloudEventType)
          .build();
    }

    String maybeReshapeData(Event legacyEvent, String jsonData) {
      return jsonData;
    }

    SourceAndSubject convertResourceToSourceAndSubject(String resourceName, Event legacyEvent) {
      return SourceAndSubject.of(resourceName, null);
    }
  }

  private static class PubSubEventAdapter extends EventAdapter {
    PubSubEventAdapter(String cloudEventType) {
      super(cloudEventType, PUB_SUB_SERVICE);
    }

    @Override
    String maybeReshapeData(Event legacyEvent, String jsonData) {
      JsonObject jsonObject = GSON.fromJson(jsonData, JsonObject.class);
      JsonObject wrapped = new JsonObject();
      wrapped.add("message", jsonObject);
      return GSON.toJson(wrapped);
    }
  }

  private static class StorageEventAdapter extends EventAdapter {
    private static final Pattern STORAGE_RESOURCE_PATTERN =
        Pattern.compile("^(projects/_/buckets/[^/]+)/(objects/.*?)(?:#\\d+)?$");

    StorageEventAdapter(String cloudEventType) {
      super(cloudEventType, STORAGE_SERVICE);
    }

    @Override
    SourceAndSubject convertResourceToSourceAndSubject(String resourceName, Event legacyEvent) {
      Matcher matcher = STORAGE_RESOURCE_PATTERN.matcher(resourceName);
      if (matcher.matches()) {
        String resource = matcher.group(1);
        String subject = matcher.group(2);
        return SourceAndSubject.of(resource, subject);
      }
      return super.convertResourceToSourceAndSubject(resourceName, legacyEvent);
    }
  }

  private static class FirestoreFirebaseEventAdapter extends EventAdapter {
    private static final Pattern FIRESTORE_RESOURCE_PATTERN =
        Pattern.compile("^(projects/.+)/((documents|refs)/.+)$");

    FirestoreFirebaseEventAdapter(String cloudEventType, String defaultService) {
      super(cloudEventType, defaultService);
    }

    @Override
    SourceAndSubject convertResourceToSourceAndSubject(String resourceName, Event legacyEvent) {
      Matcher matcher = FIRESTORE_RESOURCE_PATTERN.matcher(resourceName);
      if (matcher.matches()) {
        String resource = matcher.group(1);
        String subject = matcher.group(2);
        return SourceAndSubject.of(resource, subject);
      }
      return super.convertResourceToSourceAndSubject(resourceName, legacyEvent);
    }

    @Override
    String maybeReshapeData(Event legacyEvent, String jsonData) {
      // The reshaping code is disabled for now, because the specification for how the legacy "params"
      // field should be represented in a CloudEvent is in flux.
      if (true || legacyEvent.getContext().params().isEmpty()) {
        return jsonData;
      }
      JsonObject jsonObject = GSON.fromJson(jsonData, JsonObject.class);
      JsonObject wildcards = new JsonObject();
      legacyEvent.getContext().params().forEach((k, v) -> wildcards.addProperty(k, v));
      jsonObject.add("wildcards", wildcards);
      return GSON.toJson(jsonObject);
    }
  }

  private static class FirebaseAuthEventAdapter extends EventAdapter {
    FirebaseAuthEventAdapter(String cloudEventType) {
      super(cloudEventType, FIREBASE_AUTH_SERVICE);
    }

    @Override
    SourceAndSubject convertResourceToSourceAndSubject(String resourceName, Event legacyEvent) {
      String subject = null;
      JsonObject data = legacyEvent.getData().getAsJsonObject();
      if (data.has("uid")) {
        subject = "users/" + data.get("uid").getAsString();
      }
      return SourceAndSubject.of(resourceName, subject);
    }

    @Override
    String maybeReshapeData(Event legacyEvent, String jsonData) {
      JsonObject jsonObject = GSON.fromJson(jsonData, JsonObject.class);
      if (!jsonObject.has("metadata")) {
        return jsonData;
      }
      JsonObject metadata = jsonObject.getAsJsonObject("metadata");
      if (metadata.has("createdAt")) {
        metadata.add("createTime", metadata.get("createdAt"));
        metadata.remove("createdAt");
      }
      if (metadata.has("lastSignedInAt")) {
        metadata.add("lastSignInTime", metadata.get("lastSignedInAt"));
        metadata.remove("lastSignedInAt");
      }
      return GSON.toJson(jsonObject);
    }
  }
}
