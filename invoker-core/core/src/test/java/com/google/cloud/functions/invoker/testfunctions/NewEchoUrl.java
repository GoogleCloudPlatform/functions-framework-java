package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class NewEchoUrl implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    StringBuilder url = new StringBuilder(request.getPath());
    request.getQuery().ifPresent(q -> url.append("?").append(q));
    url.append("\n");
    response.getWriter().write(url.toString());
  }
}
