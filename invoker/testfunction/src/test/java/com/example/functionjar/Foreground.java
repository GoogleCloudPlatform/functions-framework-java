package com.example.functionjar;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class Foreground implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    // runtimeClassName is the name of some class that the runtime can see but we should not
    String runtimeClassName = request.getFirstQueryParameter("class").get();
    try {
      new Checker().serviceOrAssert(runtimeClassName);
      response.setStatusCode(200, "OK");
      response.getWriter().write("OK");
    } catch (AssertionError e) {
      response.setStatusCode(500, e.toString());
    }
  }
}
