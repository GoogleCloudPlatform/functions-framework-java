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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.truth.Expect;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Rule;
import org.junit.Test;

public class GcfEventsTest {
  @Rule public Expect expect = Expect.create();

  private static final String[][] EVENT_DATA = {
    {"storage.json", "google.cloud.storage.object.v1.finalized",
      "//storage.googleapis.com/projects/_/buckets/some-bucket", "objects/folder/Test.cs"},
    {"legacy_storage_change.json", "google.cloud.storage.object.v1.changed",
      "//storage.googleapis.com/projects/_/buckets/sample-bucket", "objects/MyFile"},
    {"firestore_simple.json", "google.cloud.firestore.document.v1.written",
      "//firestore.googleapis.com/projects/project-id/databases/(default)",
      "documents/gcf-test/2Vm2mI1d0wIaK2Waj5to"},
    {"pubsub_text.json", "google.cloud.pubsub.topic.v1.messagePublished",
      "//pubsub.googleapis.com/projects/sample-project/topics/gcf-test", null},
    {"legacy_pubsub.json", "google.cloud.pubsub.topic.v1.messagePublished",
      "//pubsub.googleapis.com/projects/sample-project/topics/gcf-test", null},
    {"firebase-db1.json", "google.firebase.database.document.v1.written",
      "//firebase.googleapis.com/projects/_/instances/my-project-id/refs/gcf-test/xyz", null},
    {"firebase-auth1.json", "google.firebase.auth.user.v1.created",
      "//firebase.googleapis.com/projects/my-project-id", null},
    {"firebase-auth2.json", "google.firebase.auth.user.v1.deleted",
      "//firebase.googleapis.com/projects/my-project-id", null},
  };

  @Test
  public void convertGcfEvent() throws IOException {
    for (String[] eventData : EVENT_DATA) {
      Event legacyEvent = legacyEventForResource(eventData[0]);
      convertGcfEvent(legacyEvent, eventData[1], eventData[2], eventData[3]);
    }
  }

  private void convertGcfEvent(Event legacyEvent, String expectedType, String expectedSource, String expectedSubject) {
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    expect.that(cloudEvent.getType()).isEqualTo(expectedType);
    expect.that(cloudEvent.getSource().toString()).isEqualTo(expectedSource);
    expect.that(cloudEvent.getSubject()).isEqualTo(expectedSubject);
  }

  // Checks everything we know about a single event.
  @Test
  public void checkAllProperties() throws IOException {
    Event legacyEvent = legacyEventForResource("storage.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    assertThat(cloudEvent.getDataContentType()).isEqualTo("application/json");
    assertThat(cloudEvent.getId()).isEqualTo("1147091835525187");
    assertThat(cloudEvent.getType()).isEqualTo("google.cloud.storage.object.v1.finalized");
    assertThat(cloudEvent.getTime())
        .isEqualTo(OffsetDateTime.of(2020, 4, 23, 7, 38, 57, 772_000_000, ZoneOffset.UTC));
    assertThat(cloudEvent.getSource().toString())
        .isEqualTo("//storage.googleapis.com/projects/_/buckets/some-bucket");
    assertThat(cloudEvent.getSubject()).isEqualTo("objects/folder/Test.cs");
    assertThat(cloudEvent.getSpecVersion()).isEqualTo(SpecVersion.V1);
    assertThat(cloudEvent.getDataSchema()).isNull();
  }

  // Checks that a PubSub event correctly gets its payload wrapped in a "message" dictionary.
  @Test
  public void pubSubWrapping() throws IOException {
    Event legacyEvent = legacyEventForResource("legacy_pubsub.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    assertThat(new String(cloudEvent.getData(), UTF_8))
        .isEqualTo("{\"message\":{\"@type\":\"type.googleapis.com/google.pubsub.v1.PubsubMessage\","
            + "\"attributes\":{\"attribute1\":\"value1\"},"
            + "\"data\":\"VGhpcyBpcyBhIHNhbXBsZSBtZXNzYWdl\"}}");
  }

  // Checks that a Firestore event correctly gets an extra "wildcards" property in its CloudEvent data
  // reflecting the "params" field in the legacy event.
  @Test
  public void firestoreWildcards() throws IOException {
    Event legacyEvent = legacyEventForResource("firestore_simple.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    JsonObject payload = new Gson().fromJson(new String(cloudEvent.getData(), UTF_8), JsonObject.class);
    JsonObject wildcards = payload.getAsJsonObject("wildcards");
    assertThat(wildcards.keySet()).containsExactly("doc");
    assertThat(wildcards.getAsJsonPrimitive("doc").getAsString()).isEqualTo("2Vm2mI1d0wIaK2Waj5to");
  }

  private Event legacyEventForResource(String resourceName) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/" + resourceName)) {
      assertWithMessage("No such resource /%s", resourceName).that(in).isNotNull();
      String legacyEventString = new String(in.readAllBytes(), UTF_8);
      return BackgroundFunctionExecutor.parseLegacyEvent(new StringReader(legacyEventString));
    }
  }
}
