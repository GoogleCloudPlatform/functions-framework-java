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

package com.google.cloud.functions.conformance;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.functions.CloudEventsFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class is used by the Functions Framework Conformance Tools to validate
 * the framework's Cloud Events API. It can be run with the following command:
 *
 * <pre>{@code
 * $ functions-framework-conformance-client \
 *   -cmd="mvn function:run -Drun.functionTarget=com.google.cloud.functions.conformance.CloudEventsConformanceFunction" \
 *   -type=cloudevent \
 *   -buildpacks=false \
 *   -validate-mapping=false \
 *   -start-delay=5
 * }</pre>
 */
public class CloudEventsConformanceFunction implements CloudEventsFunction {

  private static final Gson gson = new GsonBuilder()
      .serializeNulls()
      .setPrettyPrinting()
      .create();

  @Override
  public void accept(CloudEvent event) throws Exception {
    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter("function_output.json"));
      writer.write(serialize(event));
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  /**
   * Create a structured JSON representation of a cloud event
   */
  private String serialize(CloudEvent event) {
    JsonObject jsonEvent = new JsonObject();

    jsonEvent.addProperty("id", event.getId());
    jsonEvent.addProperty("source", event.getSource().toString());
    jsonEvent.addProperty("type", event.getType());
    jsonEvent.addProperty("datacontenttype", event.getDataContentType());
    jsonEvent.addProperty("subject", event.getSubject());
    jsonEvent.addProperty("specversion", event.getSpecVersion().toString());

    String time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
      .format(new Date(event.getTime().toInstant().toEpochMilli()));
    jsonEvent.addProperty("time", time);

    String payloadJson = new String(event.getData().toBytes(), UTF_8);
    JsonObject jsonObject = new Gson().fromJson(payloadJson, JsonObject.class);
    jsonEvent.add("data", jsonObject);

    return gson.toJson(jsonEvent);
  }
}
