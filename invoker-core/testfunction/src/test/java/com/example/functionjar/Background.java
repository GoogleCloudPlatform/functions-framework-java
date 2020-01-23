package com.example.functionjar;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class Background implements RawBackgroundFunction {
  @Override
  public void accept(String json, Context context) {
    try {
      test(json);
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    }
  }

  private void test(String jsonString) {
    Gson gson = new Gson();
    JsonObject json = gson.fromJson(jsonString, JsonObject.class);
    JsonPrimitive jsonRuntimeClassName = json.getAsJsonPrimitive("class");
    String runtimeClassName = jsonRuntimeClassName.getAsString();
    new Checker().serviceOrAssert(runtimeClassName);
  }
}
