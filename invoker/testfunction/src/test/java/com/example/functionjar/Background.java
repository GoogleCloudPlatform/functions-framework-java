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

package com.example.functionjar;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** @author emcmanus@google.com (Éamonn McManus) */
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
