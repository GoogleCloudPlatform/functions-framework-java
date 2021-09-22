package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class ExceptionHttp implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    throw new RuntimeException("exception thrown for test");
  }
}
