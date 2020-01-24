package com.google.cloud.functions.invoker;

import com.google.cloud.functions.Context;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Wrapper for user background function. */
public class BackgroundCloudFunction extends CloudFunction {

  BackgroundCloudFunction(Object o, Method m) {
    super(o, m);
  }

  void execute(JsonElement dataJson, Context context) throws InvocationTargetException {

    Object data;
    Class<?> dataParameterType = functionMethod.getParameterTypes()[0];
    if (dataParameterType == JsonElement.class) {
      data = dataJson;
    } else {
      Gson gson = new Gson();
      try {
        data = gson.fromJson(dataJson, dataParameterType);
      } catch (JsonParseException e) {
        throw new RuntimeException("Could not parse received event payload into type "
            + dataParameterType.getCanonicalName(), e);
      }
    }

    if (functionMethod.getParameterCount() == 2) {
      rawExecute(data, context);
    } else {
      rawExecute(data);
    }
  }
}
