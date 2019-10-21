package com.google.cloud.functions.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.functions.Context;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
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
        new FunctionLoader<>(fullTarget, Optional.empty(),
            new BackgroundFunctionSignatureMatcher());
    BackgroundCloudFunction function = loader.loadUserFunction();
    BackgroundFunctionExecutor executor = new BackgroundFunctionExecutor(function);
    Mockito.when(req.getReader())
        .thenReturn(
            new BufferedReader(
                new InputStreamReader(this.getClass().getResourceAsStream(eventFilePath))));

    executor.service(req, res);

    assertEquals(lastSum, 5);
    assertEquals(lastEventType, "com.example.someevent.new");
  }
}
