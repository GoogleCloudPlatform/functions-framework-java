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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import org.junit.Test;

public class CloudEventsTest {
  @Test
  public void firebaseFirestoreTest() throws Exception {
    CloudEvent cloudEvent = cloudEventForResource("firestore_complex-cloudevent-input.json");
    Event actualEvent = CloudEvents.convertToLegacyEvent(cloudEvent);

    Event expEvent = legacyEventForResource("firestore_complex-legacy-output.json");
    assertThat(actualEvent).isEqualTo(expEvent);
  }

  @Test
  public void pubSubTest() throws Exception {
    CloudEvent cloudEvent = cloudEventForResource("pubsub_text-cloudevent-input.json");
    Event actualEvent = CloudEvents.convertToLegacyEvent(cloudEvent);

    Event expEvent = legacyEventForResource("pubsub_text-legacy-output.json");
    assertThat(actualEvent).isEqualTo(expEvent);
  }

  @Test
  public void firebaseAuthTest() throws Exception {
    CloudEvent cloudEvent = cloudEventForResource("firebase-auth-cloudevent-input.json");
    Event actualEvent = CloudEvents.convertToLegacyEvent(cloudEvent);

    Event expEvent = legacyEventForResource("firebase-auth-legacy-output.json");
    assertThat(actualEvent).isEqualTo(expEvent);
  }

  @Test
  public void firebaseDb1Test() throws Exception {
    CloudEvent cloudEvent = cloudEventForResource("firebase-db1-cloudevent-input.json");
    Event actualEvent = CloudEvents.convertToLegacyEvent(cloudEvent);

    Event expEvent = legacyEventForResource("firebase-db1-legacy-output.json");
    assertThat(actualEvent).isEqualTo(expEvent);
  }

  @Test
  public void firebaseDb2Test() throws Exception {
    CloudEvent cloudEvent = cloudEventForResource("firebase-db2-cloudevent-input.json");
    Event actualEvent = CloudEvents.convertToLegacyEvent(cloudEvent);

    Event expEvent = legacyEventForResource("firebase-db2-legacy-output.json");
    assertThat(actualEvent).isEqualTo(expEvent);
  }

  @Test
  public void storageTest() throws Exception {
    CloudEvent cloudEvent = cloudEventForResource("storage-cloudevent-input.json");
    Event actualEvent = CloudEvents.convertToLegacyEvent(cloudEvent);

    Event expEvent = legacyEventForResource("storage-legacy-output.json");
    assertThat(actualEvent).isEqualTo(expEvent);
  }

  private CloudEvent cloudEventForResource(String resourceName) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/" + resourceName)) {
      assertWithMessage("No such resource /%s", resourceName).that(in).isNotNull();
      byte[] req = in.readAllBytes();
      return io.cloudevents.core.provider.EventFormatProvider.getInstance()
          .resolveFormat(JsonFormat.CONTENT_TYPE)
          .deserialize(req);
    }
  }

  private Event legacyEventForResource(String resourceName) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/" + resourceName)) {
      assertWithMessage("No such resource /%s", resourceName).that(in).isNotNull();
      String legacyEventString = new String(in.readAllBytes(), UTF_8);
      return BackgroundFunctionExecutor.parseLegacyEvent(new StringReader(legacyEventString));
    }
  }
}
