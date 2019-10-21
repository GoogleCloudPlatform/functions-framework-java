package com.google.cloud.functions.invoker.testfunctions;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EchoUrl {
  public void echoUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String url = request.getRequestURI();
    if (request.getQueryString() != null) {
      url += "?" + request.getQueryString();
    }
    response.getWriter().println(url);
  }
}
