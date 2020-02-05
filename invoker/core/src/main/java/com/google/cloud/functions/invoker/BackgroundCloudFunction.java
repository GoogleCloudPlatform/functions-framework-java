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
