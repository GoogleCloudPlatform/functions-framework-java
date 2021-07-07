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
import static java.util.Map.entry;

import com.google.common.truth.Expect;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class LegacyEventAdapterTest {
  @Rule public Expect expect = Expect.create();

  static final Map<String, String> PUBSUB_RESOURCE =
      Map.ofEntries(
          entry("service", "pubsub.googleapis.com"),
          entry("name", "projects/sample-project/topics/gcf-test"),
          entry("type", "type.googleapis.com/google.pubsub.v1.PubsubMessage"));

  static final Map<String, Map<String, String>> PUBSUB_DATA =
      Map.ofEntries(
              entry("message", Map.ofEntries(entry("foo", "bar"))));

  static final Map<String, String> STORAGE_RESOURCE =
      Map.ofEntries(
          entry("service", "storage.googleapis.com"),
          entry("name", "projects/_/buckets/some-bucket/objects/folder/Test.cs"),
          entry("type", "storage#object")
      );

  private static final List<Map<String, String>> TEST_CASES =
      Arrays.asList(
          Map.ofEntries(
              entry("ceType", "google.cloud.pubsub.topic.v1.messagePublished"),
              entry("ceSource", "//pubsub.googleapis.com/projects/sample-project/topics/gcf-test"),
              entry("eventType", "google.pubsub.topic.publish"),
              entry("resource", new Gson().toJson(PUBSUB_RESOURCE)),
              entry("ceData", new Gson().toJson(PUBSUB_DATA)),
              entry("data", "{\"foo\": \"bar\"}")),
          Map.ofEntries(
              entry("ceType", "google.cloud.storage.object.v1.finalized"),
              entry("ceSource", "//storage.googleapis.com/projects/_/buckets/some-bucket"),
              entry("ceSubject", "objects/folder/Test.cs"),
              entry("eventType", "google.storage.object.finalize"),
              entry("ceData", "{\"kind\":\"storage#object\"}"),
              entry("resource", new Gson().toJson(STORAGE_RESOURCE))),
          Map.ofEntries(
              entry("ceType", "google.firebase.database.document.v1.written"),
              entry(
                  "ceSource",
                  "//firebasedatabase.googleapis.com/projects/_/locations/us-central1/instances/my-project-id"),
              entry("ceSubject", "refs/gcf-test/xyz"),
              entry("eventType", "providers/google.firebase.database/eventTypes/ref.write"),
              entry("resource", "\"projects/_/instances/my-project-id/refs/gcf-test/xyz\"")));

  @Test
  public void convertToLegacyEvent() throws IOException {
    for (Map<String, String> testCase : TEST_CASES) {
      final CloudEvent event =
          CloudEventBuilder.v1()
              .withId("000")
              .withType(testCase.get("ceType"))
              .withSource(URI.create(testCase.get("ceSource")))
              .withDataContentType("application/json")
              .withSubject(testCase.get("ceSubject"))
              .withData(testCase.getOrDefault("ceData", "{}").getBytes())
              .build();

      Event e = LegacyEventAdapter.convertToLegacyEvent(event);

      CloudFunctionsContext context = e.getContext();
      assertThat(context.eventId()).isEqualTo("000");
      assertThat(context.eventType()).isEqualTo(testCase.get("eventType"));
      assertThat(toJson(context.resource())).isEqualTo(toJson(testCase.get("resource")));
      
      if (testCase.containsKey("data")) {
        assertThat(e.getData()).isEqualTo(toJson(testCase.get("data")));
      }
    }
  }

  private JsonElement toJson(String data) {
    return new Gson().fromJson(data, JsonElement.class);
  }
}
