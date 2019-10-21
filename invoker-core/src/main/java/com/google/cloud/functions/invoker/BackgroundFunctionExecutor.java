package com.google.cloud.functions.invoker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's background function. */
public class BackgroundFunctionExecutor extends HttpServlet {

  private final BackgroundCloudFunction function;

  public BackgroundFunctionExecutor(BackgroundCloudFunction function) {
    this.function = function;
  }

  /** Executes the user's background function, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    BufferedReader body = req.getReader();

    // A Type Adapter is required to set the type of the JsonObject because CloudFunctionsContext
    // is abstract and Gson default behavior instantiates the type provided.
    TypeAdapter<CloudFunctionsContext> typeAdapter =
        CloudFunctionsContext.typeAdapter(new Gson());
    Gson gson = new GsonBuilder()
        .registerTypeAdapter(CloudFunctionsContext.class, typeAdapter)
        .registerTypeAdapter(Event.class, new Event.EventDeserializer())
        .create();

    Event event = gson.fromJson(body, Event.class);
    try {
      function.execute(event.getData(), event.getContext());
      res.setStatus(HttpServletResponse.SC_OK);
    } catch (InvocationTargetException e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      e.getCause().printStackTrace();
    }
  }
}
