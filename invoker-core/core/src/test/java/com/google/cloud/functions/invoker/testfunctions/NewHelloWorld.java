package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class NewHelloWorld implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    response.getWriter().write("hello\n");
  }
}
