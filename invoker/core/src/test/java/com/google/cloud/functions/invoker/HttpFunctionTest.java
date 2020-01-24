package com.google.cloud.functions.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.mockito.Mockito;

public class HttpFunctionTest {

  private static String lastResponse = "";

  public static class HttpWriter {

    public void writeResponse(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      PrintWriter writer = response.getWriter();
      writer.write(request.getParameter("data"));
    }
  }

  @Test
  public void adder() throws Exception {
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    String fullTarget =
        "com.google.cloud.functions.invoker.HttpFunctionTest$HttpWriter.writeResponse";
    String requestData = "testData";
    FunctionLoader<HttpCloudFunction> loader = new FunctionLoader<>(
        fullTarget, getClass().getClassLoader(), new HttpFunctionSignatureMatcher());
    HttpCloudFunction function = loader.loadUserFunction();
    HttpFunctionExecutor executor = new HttpFunctionExecutor(function);
    Mockito.when(req.getParameter("data")).thenReturn(requestData);

    Mockito.when(res.getWriter()).thenReturn(new MockPrintWriter("fooFile"));

    executor.service(req, res);

    assertEquals(lastResponse, requestData);
  }

  private class MockPrintWriter extends java.io.PrintWriter {

    public MockPrintWriter(String s) throws FileNotFoundException {
      super(s);
    }

    public void write(String s) {
      lastResponse = s;
    }
  }
}
