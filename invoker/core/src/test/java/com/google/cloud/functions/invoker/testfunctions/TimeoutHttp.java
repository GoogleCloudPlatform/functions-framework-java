package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class TimeoutHttp implements HttpFunction {

  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      response.getWriter().close();
    }
    response.getWriter().write("finished\n");
  }
}
