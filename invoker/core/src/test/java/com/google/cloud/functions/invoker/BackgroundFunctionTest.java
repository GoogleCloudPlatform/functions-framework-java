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

import com.google.cloud.functions.Context;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class BackgroundFunctionTest {

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {"JsonAdder.add", "/adder_gcf_ga_event.json"},
        {"CustomTypeAdder.add", "/adder_gcf_ga_event.json"},
        {"JsonAdder.add", "/adder_gcf_beta_event.json"},
        {"CustomTypeAdder.add", "/adder_gcf_beta_event.json"},
        {"JsonAdder.add", "/adder_gcf_beta_event_json_resource.json"},
        {"CustomTypeAdder.add", "/adder_gcf_beta_event_json_resource.json"},
    });
  }

  @Parameter(0)
  public String target;

  @Parameter(1)
  public String eventFilePath;

  private static int lastSum = 0;
  private static String lastEventType = "";

  public static class JsonAdder {

    public void add(JsonElement data, Context context) {
      lastSum =
          data.getAsJsonObject().get("a").getAsInt() + data.getAsJsonObject().get("b").getAsInt();
      lastEventType = context.eventType();
    }
  }

  public static class CustomTypeAdder {

    public static class Data {

      int a;
      int b;
    }

    public void add(Data data, Context context) {
      lastSum = data.a + data.b;
      lastEventType = context.eventType();
    }
  }

  @Test
  public void adder() throws Exception {
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    String fullTarget = "com.google.cloud.functions.invoker.BackgroundFunctionTest$" + target;
    FunctionLoader<BackgroundCloudFunction> loader =
        new FunctionLoader<>(
            fullTarget,
            getClass().getClassLoader(),
            new BackgroundFunctionSignatureMatcher(),
            null);
    BackgroundCloudFunction function = loader.loadUserFunction();
    BackgroundFunctionExecutor executor = new BackgroundFunctionExecutor(function);
    Mockito.when(req.getReader())
        .thenReturn(
            new BufferedReader(
                new InputStreamReader(this.getClass().getResourceAsStream(eventFilePath))));

    executor.service(req, res);

    assertThat(lastSum).isEqualTo(5);
    assertThat(lastEventType).isEqualTo("com.example.someevent.new");
  }
}
