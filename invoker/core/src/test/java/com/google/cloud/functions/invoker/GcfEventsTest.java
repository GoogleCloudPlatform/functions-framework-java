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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Ignore;
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
    {"firebase-db1.json", "google.firebase.database.ref.v1.written",
      "//firebasedatabase.googleapis.com/projects/_/locations/us-central1/instances/my-project-id",
      "refs/gcf-test/xyz"},
    {"firebase-db2.json", "google.firebase.database.ref.v1.written",
      "//firebasedatabase.googleapis.com/projects/_/locations/europe-west1/instances/my-project-id",
      "refs/gcf-test/xyz"},
    {"firebase-auth1.json", "google.firebase.auth.user.v1.created",
      "//firebaseauth.googleapis.com/projects/my-project-id", "users/UUpby3s4spZre6kHsgVSPetzQ8l2"},
    {"firebase-auth2.json", "google.firebase.auth.user.v1.deleted",
      "//firebaseauth.googleapis.com/projects/my-project-id", "users/UUpby3s4spZre6kHsgVSPetzQ8l2"},
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

  // The next set of tests checks the result of using Gson to deserialize the JSON "data" field of the
  // CloudEvent that we get from converting a legacy event. For the most part we're not testing much here,
  // since the "data" field is essentially copied from the input legacy event. In some cases we adjust it,
  // though.
  // Later, when we have support for handling these types properly in Java, we can change the tests to use
  // that. See https://github.com/googleapis/google-cloudevents-java

  @Test
  public void storageData() throws IOException {
    Event legacyEvent = legacyEventForResource("storage.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    Map<String, Object> data = cloudEventDataJson(cloudEvent);
    assertThat(data).containsAtLeast(
        "bucket", "some-bucket",
        "timeCreated", "2020-04-23T07:38:57.230Z",
        "generation", "1587627537231057",
        "metageneration", "1",
        "size", "352");
  }

  @Test
  public void firestoreSimpleData() throws IOException {
    Event legacyEvent = legacyEventForResource("firestore_simple.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    Map<String, Object> data = cloudEventDataJson(cloudEvent);
    Map<String, Object> expectedValue = Map.of(
        "name", "projects/project-id/databases/(default)/documents/gcf-test/2Vm2mI1d0wIaK2Waj5to",
        "createTime", "2020-04-23T09:58:53.211035Z",
        "updateTime", "2020-04-23T12:00:27.247187Z",
        "fields", Map.of(
            "another test", Map.of("stringValue", "asd"),
            "count", Map.of("integerValue", "4"),
            "foo", Map.of("stringValue", "bar")));
    Map<String, Object> expectedOldValue = Map.of(
        "name", "projects/project-id/databases/(default)/documents/gcf-test/2Vm2mI1d0wIaK2Waj5to",
        "createTime", "2020-04-23T09:58:53.211035Z",
        "updateTime", "2020-04-23T12:00:27.247187Z",
        "fields", Map.of(
            "another test", Map.of("stringValue", "asd"),
            "count", Map.of("integerValue", "3"),
            "foo", Map.of("stringValue", "bar")));
    assertThat(data).containsAtLeast(
        "value", expectedValue,
        "oldValue", expectedOldValue,
        "updateMask", Map.of("fieldPaths", List.of("count")));
  }

  @Test
  public void firestoreComplexData() throws IOException {
    Event legacyEvent = legacyEventForResource("firestore_complex.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    Map<String, Object> data = cloudEventDataJson(cloudEvent);
    Map<?, ?> value = (Map<?, ?>) data.get("value");
    Map<?, ?> fields = (Map<?, ?>) value.get("fields");
    Map<String, Object> expectedFields = Map.of(
        "arrayValue", Map.of("arrayValue",
            Map.of("values",
                List.of(Map.of("integerValue", "1"), Map.of("integerValue", "2")))),
        "booleanValue", Map.of("booleanValue", true),
        "geoPointValue", Map.of("geoPointValue", Map.of("latitude", 51.4543, "longitude", -0.9781)),
        "intValue", Map.of("integerValue", "50"),
        "doubleValue", Map.of("doubleValue", 5.5),
        "nullValue", Collections.singletonMap("nullValue", null),
        "referenceValue", Map.of("referenceValue",
            "projects/project-id/databases/(default)/documents/foo/bar/baz/qux"),
        "stringValue", Map.of("stringValue", "text"),
        "timestampValue", Map.of("timestampValue", "2020-04-23T14:23:53.241Z"),
        "mapValue", Map.of("mapValue",
            Map.of("fields",
                Map.of("field1", Map.of("stringValue", "x"),
                    "field2", Map.of("arrayValue",
                        Map.of("values",
                            List.of(Map.of("stringValue", "x"), Map.of("integerValue", "1")))))))
    );
    assertThat(fields).containsExactlyEntriesIn(expectedFields);
  }

  @Test
  public void pubSubTextData() throws IOException {
    Event legacyEvent = legacyEventForResource("pubsub_text.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    Map<String, Object> data = cloudEventDataJson(cloudEvent);

    Map<?, ?> message = (Map<?, ?>) data.get("message");
    assertThat(message).isNotNull();
    assertThat(message).containsKey("data");
    // Later we should provide support for doing this more simply and test that:
    String base64 = (String) message.get("data");
    byte[] bytes = Base64.getDecoder().decode(base64);
    String text = new String(bytes, UTF_8);
    assertThat(text).isEqualTo("test message 3");

    assertThat(message).containsEntry("attributes", Map.of("attr1", "attr1-value"));
  }

  @Test
  public void pubSubBinaryData() throws IOException {
    Event legacyEvent = legacyEventForResource("pubsub_binary.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    Map<String, Object> data = cloudEventDataJson(cloudEvent);

    Map<?, ?> message = (Map<?, ?>) data.get("message");
    assertThat(message).isNotNull();
    assertThat(message).containsKey("data");
    // Later we should provide support for doing this more simply and test that:
    String base64 = (String) message.get("data");
    byte[] bytes = Base64.getDecoder().decode(base64);
    assertThat(bytes).isEqualTo(new byte[] {1, 2, 3, 4});

    assertThat(message).doesNotContainKey("attributes");
  }

  // Checks that a PubSub event correctly gets its payload wrapped in a "message" dictionary.
  @Test
  public void pubSubWrapping() throws IOException {
    Event legacyEvent = legacyEventForResource("legacy_pubsub.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    assertThat(new String(cloudEvent.getData().toBytes(), UTF_8))
        .isEqualTo("{\"message\":{\"@type\":\"type.googleapis.com/google.pubsub.v1.PubsubMessage\","
            + "\"attributes\":{\"attribute1\":\"value1\"},"
            + "\"data\":\"VGhpcyBpcyBhIHNhbXBsZSBtZXNzYWdl\","
            + "\"messageId\":\"1215011316659232\","
            + "\"publishTime\":\"2020-05-18T12:13:19.209Z\"}}");
  }

  // Checks that a Firestore event correctly gets an extra "wildcards" property in its CloudEvent data
  // reflecting the "params" field in the legacy event.
  // This test is currently ignored because the final representation of the "params" field is in flux.
  @Test
  @Ignore
  public void firestoreWildcards() throws IOException {
    Event legacyEvent = legacyEventForResource("firestore_simple.json");
    CloudEvent cloudEvent = GcfEvents.convertToCloudEvent(legacyEvent);
    JsonObject payload =
        new Gson().fromJson(new String(cloudEvent.getData().toBytes(), UTF_8), JsonObject.class);
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

  private static Map<String, Object> cloudEventDataJson(CloudEvent cloudEvent) {
    String data = new String(cloudEvent.getData().toBytes(), UTF_8);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = new Gson().fromJson(data, Map.class);
    return map;
  }
}
