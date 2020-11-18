package com.google.cloud.functions.invoker.testfunctions;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.functions.ExperimentalCloudEventsFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import java.io.FileOutputStream;

public class CloudEventSnoop implements ExperimentalCloudEventsFunction {
  @Override
  public void accept(CloudEvent event) throws Exception {
    String payloadJson = new String(event.getData().toBytes(), UTF_8);
    Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(payloadJson, JsonObject.class);
    String targetFile = jsonObject.get("targetFile").getAsString();
    if (targetFile == null) {
      throw new IllegalArgumentException("Expected targetFile in JSON payload");
    }
    EventFormat jsonFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
    byte[] bytes = jsonFormat.serialize(event);
    try (FileOutputStream out = new FileOutputStream(targetFile)) {
      out.write(bytes);
    }
  }
}
