package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;
import java.util.List;
import java.util.Map;

public class BufferedWrites implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    Map<String, List<String>> queryParameters = request.getQueryParameters();
    int writes = Integer.parseInt(request.getFirstQueryParameter("writes").orElse("0"));
    boolean flush = Boolean.parseBoolean(request.getFirstQueryParameter("flush").orElse("false"));

    BufferedWriter writer = response.getWriter();
    for (int i = 0; i < writes; i++) {
      response.appendHeader("x-write-" + i, "true");
      writer.write("write " + i + "\n");
    }
    if (flush) {
      writer.flush();
    }
    response.appendHeader("x-written", "true");
  }
}
