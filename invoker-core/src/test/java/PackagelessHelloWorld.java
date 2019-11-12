// A function in the default package.

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class PackagelessHelloWorld implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    response.setContentType("text/plain; charset=utf-8");
    response.getWriter().write("hello, world\n");
  }
}
