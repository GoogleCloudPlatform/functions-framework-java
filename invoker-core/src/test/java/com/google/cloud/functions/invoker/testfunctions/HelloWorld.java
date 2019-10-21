package com.google.cloud.functions.invoker.testfunctions;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HelloWorld {
  public void helloWorld(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.getWriter().println("hello");
  }
}
