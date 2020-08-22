package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

/**
 * Extract the targetFile property from the data of the JSON payload, and write to it a JSON
 * encoding of this payload and the context. The JSON format is chosen to be identical to the
 * EventFlow format that we currently use in GCF, and the file that we write should in fact be
 * identical to the JSON payload that the Functions Framework received from the client in the test.
 * This will need to be rewritten when we switch to CloudEvents.
 */
public class BackgroundSnoop implements RawBackgroundFunction {
  @Override
  public void accept(String json, Context context) {
    Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
    String targetFile = jsonObject.get("targetFile").getAsString();
    if (targetFile == null) {
      throw new IllegalArgumentException("Expected targetFile in JSON payload");
    }
    JsonObject resourceJson = gson.fromJson(context.resource(), JsonObject.class);
    JsonObject contextJson = new JsonObject();
    contextJson.addProperty("eventId", context.eventId());
    contextJson.addProperty("timestamp", context.timestamp());
    contextJson.addProperty("eventType", context.eventType());
    contextJson.add("resource", resourceJson);
    JsonObject contextAndPayloadJson = new JsonObject();
    contextAndPayloadJson.add("data", jsonObject);
    contextAndPayloadJson.add("context", contextJson);
    contextAndPayloadJson.add("attributes", gson.toJsonTree(context.attributes()));
    try (FileWriter fileWriter = new FileWriter(targetFile);
        PrintWriter writer = new PrintWriter(fileWriter)) {
      writer.println(contextAndPayloadJson);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
