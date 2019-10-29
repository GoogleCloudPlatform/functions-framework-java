package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.util.stream.Collectors;

public class NewEcho implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    String body = request.getReader().lines().collect(Collectors.joining("\n")) + "\n";
    response.setContentType("text/plain");
    response.getWriter().write(body);
    response.getWriter().flush();
  }
}
