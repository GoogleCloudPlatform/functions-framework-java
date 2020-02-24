package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Collectors;

public class NewEcho implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    boolean binary = "application/octet-stream".equals(request.getContentType().orElse(null));
    if (binary) {
      response.setContentType("application/octet-stream");
      byte[] buf = new byte[1024];
      InputStream in = request.getInputStream();
      OutputStream out = response.getOutputStream();
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
    } else {
      String body = request.getReader().lines().collect(Collectors.joining("\n")) + "\n";
      response.setContentType("text/plain");
      response.getWriter().write(body);
    }
  }
}
