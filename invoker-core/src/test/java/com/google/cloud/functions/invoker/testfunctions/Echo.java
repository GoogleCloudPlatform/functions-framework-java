package com.google.cloud.functions.invoker.testfunctions;

import java.io.IOException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Echo {
  public void echo(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String body = request.getReader().lines().collect(Collectors.joining("\n")) + "\n";
    response.setContentType("text/plain");
    response.getWriter().write(body);
    response.getWriter().flush();
  }
}
