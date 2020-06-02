package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpRequest.HttpPart;
import com.google.cloud.functions.HttpResponse;
import java.io.PrintWriter;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A simple proof-of-concept function for multipart handling.
 *
 * {@code HttpTest} contains more detailed testing, but this function is part of the
 * integration test that shows that we can indeed access the multipart API from a function.
 */
public class Multipart implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    response.setContentType("text/plain");
    String contentType = request.getContentType().orElse("<unknown>");
    if (!contentType.startsWith("multipart/form-data")) {
      response.getWriter().write("Content-Type is " + contentType + " not multipart/form-data");
      return;
    }
    PrintWriter writer = new PrintWriter(response.getWriter());
    NavigableMap<String, HttpPart> parts = new TreeMap<>(request.getParts());
    parts.forEach(
        (name, contents) -> {
          writer.printf(
              "part %s type %s length %d\n",
              name, contents.getContentType().get(), contents.getContentLength());
        }
    );
  }
}
