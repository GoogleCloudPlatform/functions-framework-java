package com.google.cloud.functions.conformance;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * This class is used by the Functions Framework Conformance Tools to validate the framework's
 * Background Event API. It can be run with the following command:
 *
 * <pre>{@code
 * $ functions-framework-conformance-client \
 *   -cmd="mvn function:run -Drun.functionTarget=com.google.cloud.functions.conformance.BackgroundEventConformanceFunction" \
 *   -type=legacyevent \
 *   -buildpacks=false \
 *   -validate-mapping=false \
 *   -start-delay=10
 * }</pre>
 */
public class BackgroundEventConformanceFunction implements RawBackgroundFunction {

  private static final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

  @Override
  public void accept(String data, Context context) throws Exception {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter("function_output.json"))) {
      writer.write(serialize(data, context));
    }
  }

  /** Create a structured JSON representation of the request context and data */
  private String serialize(String data, Context context) {
    JsonObject contextJson = new JsonObject();
    contextJson.addProperty("eventId", context.eventId());
    contextJson.addProperty("timestamp", context.timestamp());
    contextJson.addProperty("eventType", context.eventType());

    if (context.resource().startsWith("{")) {
      JsonElement resource = gson.fromJson(context.resource(), JsonElement.class);
      contextJson.add("resource", resource);
    } else {
      contextJson.addProperty("resource", context.resource());
    }

    JsonObject dataJson = gson.fromJson(data, JsonObject.class);

    JsonObject json = new JsonObject();
    json.add("data", dataJson);
    json.add("context", contextJson);
    return gson.toJson(json);
  }
}
