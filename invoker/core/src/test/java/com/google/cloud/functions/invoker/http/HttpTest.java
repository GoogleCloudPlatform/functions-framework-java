// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.functions.invoker.http;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpRequest.HttpPart;
import com.google.cloud.functions.HttpResponse;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.eclipse.jetty.client.ByteBufferRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.MultiPartRequestContent;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpStatus.Code;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartConfig;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.EagerContentHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.BeforeClass;
import org.junit.Test;

public class HttpTest {

  private static final String TEST_BODY =
      "In the reign of James the Second\n"
          + "It was generally reckoned\n"
          + "As a rather serious crime\n"
          + "To marry two wives at a time.\n";

  private static final byte[] RANDOM_BYTES = new byte[1024];

  static {
    new Random().nextBytes(RANDOM_BYTES);
  }

  private static int serverPort;

  /**
   * Each test method will start up a server on the same port, make one or more HTTP requests to
   * that port, then kill the server. So the port should be free when the next test method runs.
   */
  @BeforeClass
  public static void allocateServerPort() throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    serverPort = serverSocket.getLocalPort();
    serverSocket.close();
  }

  /**
   * Wrapper class that allows us to start a Jetty server with a single handler for {@code /*}
   * within a try-with-resources statement. The handler will be configured to support multipart
   * requests.
   */
  private static class SimpleServer implements AutoCloseable {
    private final Server server;

    SimpleServer(Handler handler) throws Exception {
      this.server = new Server(serverPort);
      server.setHandler(handler);

      MultiPartConfig config = new MultiPartConfig.Builder().maxMemoryPartSize(-1).build();
      EagerContentHandler.MultiPartContentLoaderFactory factory =
          new EagerContentHandler.MultiPartContentLoaderFactory(config);
      server.insertHandler(new EagerContentHandler(factory));

      server.start();
    }

    @Override
    public void close() throws Exception {
      server.stop();
    }
  }

  @FunctionalInterface
  private interface HttpRequestTest {
    void test(HttpRequest request) throws Exception;
  }

  /**
   * Tests methods on the {@link HttpRequest} object while the request is being serviced. We are not
   * guaranteed that the underlying {@link Request} object will still be valid when the request
   * completes, and in fact in Jetty it isn't. So we perform the checks in the context of the
   * handler, and report any exception back to the test method.
   */
  @Test
  public void httpRequestMethods() throws Exception {
    AtomicReference<HttpRequestTest> testReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    HttpRequestHandler testHandler = new HttpRequestHandler(testReference, exceptionReference);
    try (SimpleServer server = new SimpleServer(testHandler)) {
      httpRequestMethods(testReference, exceptionReference);
    }
  }

  private void httpRequestMethods(
      AtomicReference<HttpRequestTest> testReference, AtomicReference<Throwable> exceptionReference)
      throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.start();
    String uri = "http://localhost:" + serverPort + "/foo/bar?baz=buh&baz=xxx&blim=blam&baz=what";
    HttpRequestTest[] tests = {
      request -> assertThat(request.getMethod()).isEqualTo("POST"),
      request -> assertThat(request.getMethod()).isEqualTo("POST"),
      request -> assertThat(request.getUri()).isEqualTo(uri),
      request -> assertThat(request.getPath()).isEqualTo("/foo/bar"),
      request -> assertThat(request.getQuery()).hasValue("baz=buh&baz=xxx&blim=blam&baz=what"),
      request -> {
        Map<String, List<String>> expectedQueryParameters = new TreeMap<>();
        expectedQueryParameters.put("baz", Arrays.asList("buh", "xxx", "what"));
        expectedQueryParameters.put("blim", Arrays.asList("blam"));
        assertThat(request.getQueryParameters()).isEqualTo(expectedQueryParameters);
      },
      request -> assertThat(request.getFirstQueryParameter("baz")).hasValue("buh"),
      request -> assertThat(request.getFirstQueryParameter("something")).isEmpty(),
      request ->
          assertThat(request.getContentType().get())
              .ignoringCase()
              .isEqualTo("text/plain; charset=utf-8"),
      request -> assertThat(request.getContentLength()).isEqualTo(TEST_BODY.length()),
      request -> assertThat(request.getCharacterEncoding()).isPresent(),
      request -> assertThat(request.getCharacterEncoding().get()).ignoringCase().isEqualTo("utf-8"),
      request -> {
        try (BufferedReader reader = request.getReader()) {
          validateReader(reader);
          assertThat(request.getReader()).isSameInstanceAs(reader);
        }
        try {
          request.getInputStream();
          fail("Did not get expected exception");
        } catch (IllegalStateException expected) {
        }
      },
      request -> {
        try (InputStream inputStream = request.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
          validateReader(reader);
          assertThat(request.getInputStream()).isSameInstanceAs(inputStream);
        }
      },
      request -> {
        Map<String, List<String>> expectedHeaders = new TreeMap<>();
        expectedHeaders.put(
            HttpHeader.CONTENT_LENGTH.asString(),
            Arrays.asList(String.valueOf(TEST_BODY.length())));
        expectedHeaders.put("foo", Arrays.asList("bar", "baz"));
        assertThat(request.getHeaders()).containsAtLeastEntriesIn(expectedHeaders);
      },
      request -> assertThat(request.getFirstHeader("foo")).hasValue("bar"),
      request -> assertThat(request.getFirstHeader("CaSe-SeNsItIvE")).hasValue("VaLuE"),
      request -> assertThat(request.getFirstHeader("case-sensitive")).hasValue("VaLuE"),
      request -> {
        try {
          request.getParts();
          fail("Did not get expected exception");
        } catch (IllegalStateException expected) {
        }
      }
    };
    for (HttpRequestTest test : tests) {
      testReference.set(test);
      org.eclipse.jetty.client.Request request =
          httpClient
              .POST(uri)
              .headers(
                  m -> {
                    m.add(HttpHeader.CONTENT_TYPE, "text/plain; charset=utf-8");
                    m.add("foo", "bar");
                    m.add("foo", "baz");
                    m.add("CaSe-SeNsItIvE", "VaLuE");
                  })
              .body(new StringRequestContent(TEST_BODY));
      ContentResponse response = request.send();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
      throwIfNotNull(exceptionReference.get());
    }
  }

  @Test
  public void emptyRequest() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.start();
    String uri = "http://localhost:" + serverPort;
    HttpRequestTest test =
        request -> {
          assertThat(request.getUri()).isEqualTo(uri + "/");
          assertThat(request.getPath()).isEqualTo("/");
          assertThat(request.getQuery()).isEmpty();
          assertThat(request.getQueryParameters()).isEmpty();
          assertThat(request.getContentType()).isEmpty();
          assertThat(request.getContentLength()).isEqualTo(0L);
        };
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    AtomicReference<HttpRequestTest> testReference = new AtomicReference<>(test);
    HttpRequestHandler testHandler = new HttpRequestHandler(testReference, exceptionReference);
    try (SimpleServer server = new SimpleServer(testHandler)) {
      ContentResponse response = httpClient.POST(uri).send();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
      throwIfNotNull(exceptionReference.get());
    }
  }

  private void validateReader(BufferedReader reader) {
    String text = reader.lines().collect(Collectors.joining("\n", "", "\n"));
    assertThat(text).isEqualTo(TEST_BODY);
  }

  @Test
  public void multiPartRequest() throws Exception {
    AtomicReference<HttpRequestTest> testReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    HttpRequestHandler testHandler = new HttpRequestHandler(testReference, exceptionReference);
    HttpClient httpClient = new HttpClient();
    httpClient.start();
    String uri = "http://localhost:" + serverPort + "/";
    MultiPartRequestContent multiPart = new MultiPartRequestContent();
    HttpFields textHttpFields = HttpFields.build().add("foo", "bar");
    multiPart.addPart(
        new MultiPart.ContentSourcePart(
            "text", null, textHttpFields, new StringRequestContent(TEST_BODY)));
    HttpFields.Mutable bytesHttpFields = HttpFields.build().add("foo", "baz").add("foo", "buh");
    assertThat(bytesHttpFields.getValuesList("foo")).containsExactly("baz", "buh");
    multiPart.addPart(
        new MultiPart.ContentSourcePart(
            "binary",
            "/tmp/binary.x",
            bytesHttpFields,
            new ByteBufferRequestContent(ByteBuffer.wrap(RANDOM_BYTES))));
    multiPart.close();
    HttpRequestTest test =
        request -> {
          // The Content-Type header will also have a boundary=something attribute.
          assertThat(request.getContentType().get()).startsWith("multipart/form-data");
          assertThat(request.getParts().keySet()).containsExactly("text", "binary");
          HttpPart textPart = request.getParts().get("text");
          assertThat(textPart.getFileName()).isEmpty();
          assertThat(textPart.getContentLength()).isEqualTo(TEST_BODY.length());
          assertThat(textPart.getContentType().get()).startsWith("text/plain");
          assertThat(textPart.getCharacterEncoding()).isPresent();
          assertThat(textPart.getCharacterEncoding().get()).ignoringCase().isEqualTo("utf-8");
          assertThat(textPart.getHeaders()).containsAtLeast("foo", Arrays.asList("bar"));
          assertThat(textPart.getFirstHeader("foo")).hasValue("bar");
          validateReader(textPart.getReader());
          HttpPart bytesPart = request.getParts().get("binary");
          assertThat(bytesPart.getFileName()).hasValue("/tmp/binary.x");
          assertThat(bytesPart.getContentLength()).isEqualTo(RANDOM_BYTES.length);
          assertThat(bytesPart.getContentType()).hasValue("application/octet-stream");
          List<String> foos = bytesPart.getHeaders().get("foo");
          assertThat(foos).containsExactly("baz", "buh");

          byte[] bytes = new byte[RANDOM_BYTES.length];
          try (InputStream inputStream = bytesPart.getInputStream()) {
            assertThat(inputStream.read(bytes)).isEqualTo(bytes.length);
            assertThat(inputStream.read()).isEqualTo(-1);
            assertThat(bytes).isEqualTo(RANDOM_BYTES);
          }
        };
    try (SimpleServer server = new SimpleServer(testHandler)) {
      testReference.set(test);
      org.eclipse.jetty.client.Request request =
          httpClient.POST(uri).headers(m -> m.put("foo", "oof")).body(multiPart);
      ContentResponse response = request.send();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
      throwIfNotNull(exceptionReference.get());
    }
  }

  private static class HttpRequestHandler extends Handler.Abstract {
    private final AtomicReference<HttpRequestTest> testReference;
    private final AtomicReference<Throwable> exceptionReference;

    private HttpRequestHandler(
        AtomicReference<HttpRequestTest> testReference,
        AtomicReference<Throwable> exceptionReference) {
      this.testReference = testReference;
      this.exceptionReference = exceptionReference;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
      try {
        testReference.get().test(new HttpRequestImpl(request));
      } catch (Throwable t) {
        exceptionReference.set(t);
        Response.writeError(request, response, callback, t);
      }
      callback.succeeded();
      return true;
    }
  }

  @FunctionalInterface
  private interface HttpResponseTest {
    void test(HttpResponse response) throws Exception;
  }

  /**
   * Tests interactions with the {@link HttpResponse} object while the request is still ongoing. For
   * example, if we append a header then we should see that header in {@link
   * HttpResponse#getHeaders()}.
   */
  @Test
  public void httpResponseSetAndGet() throws Exception {
    AtomicReference<HttpResponseTest> testReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    HttpResponseHandler testHandler = new HttpResponseHandler(testReference, exceptionReference);
    try (SimpleServer server = new SimpleServer(testHandler)) {
      httpResponseSetAndGet(testReference, exceptionReference);
    }
  }

  private void httpResponseSetAndGet(
      AtomicReference<HttpResponseTest> testReference,
      AtomicReference<Throwable> exceptionReference)
      throws Exception {
    HttpResponseTest[] tests = {
      response -> assertThat(response.getContentType()).isEmpty(),
      response -> {
        response.setContentType("text/plain; charset=utf-8");
        assertThat(response.getContentType().get()).matches("(?i)text/plain;\\s*charset=utf-8");
      },
      response -> {
        response.appendHeader("Content-Type", "application/octet-stream");
        assertThat(response.getContentType()).hasValue("application/octet-stream");
        assertThat(response.getHeaders())
            .containsAtLeast("Content-Type", Arrays.asList("application/octet-stream"));
      },
      response -> {
        // The fields are initialized with a Date header as per the HTTP RFCs.
        // So we just check that we can add our own headers.
        response.appendHeader("foo", "bar");
        response.appendHeader("wibbly", "wobbly");
        response.appendHeader("FoO", "baz");
        var updatedHeaders = response.getHeaders();
        assertThat(updatedHeaders)
            .containsAtLeast("foo", Arrays.asList("bar", "baz"), "wibbly", Arrays.asList("wobbly"));
      },
    };
    for (HttpResponseTest test : tests) {
      testReference.set(test);
      HttpClient httpClient = new HttpClient();
      httpClient.start();
      String uri = "http://localhost:" + serverPort;
      org.eclipse.jetty.client.Request request = httpClient.POST(uri);
      ContentResponse response = request.send();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
      throwIfNotNull(exceptionReference.get());
    }
  }

  private static class HttpResponseHandler extends Handler.Abstract {
    private final AtomicReference<HttpResponseTest> testReference;
    private final AtomicReference<Throwable> exceptionReference;

    private HttpResponseHandler(
        AtomicReference<HttpResponseTest> testReference,
        AtomicReference<Throwable> exceptionReference) {
      this.testReference = testReference;
      this.exceptionReference = exceptionReference;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
      try {
        testReference.get().test(new HttpResponseImpl(response));
        callback.succeeded();
      } catch (Throwable t) {
        exceptionReference.set(t);
        Response.writeError(request, response, callback, t);
      }
      return true;
    }
  }

  @FunctionalInterface
  private interface ResponseCheck {
    void test(ContentResponse response);
  }

  private static class ResponseTest {
    final HttpResponseTest responseOperation;
    final ResponseCheck responseCheck;

    private ResponseTest(HttpResponseTest responseOperation, ResponseCheck responseCheck) {
      this.responseOperation = responseOperation;
      this.responseCheck = responseCheck;
    }
  }

  private static ResponseTest responseTest(
      HttpResponseTest responseOperation, ResponseCheck responseCheck) {
    return new ResponseTest(responseOperation, responseCheck);
  }

  /**
   * Tests that operations on the {@link HttpResponse} have the appropriate effect on the HTTP
   * response that ends up being sent. Here, for each check, we have two operations: the operation
   * on the {@link HttpResponse}, which happens inside the handler, and the operation to check the
   * HTTP result, which happens in the client thread.
   */
  @Test
  public void httpResponseEffects() throws Exception {
    AtomicReference<HttpResponseTest> testReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    HttpResponseHandler testHandler = new HttpResponseHandler(testReference, exceptionReference);
    try (SimpleServer server = new SimpleServer(testHandler)) {
      httpResponseEffects(testReference, exceptionReference);
    }
  }

  private void httpResponseEffects(
      AtomicReference<HttpResponseTest> testReference,
      AtomicReference<Throwable> exceptionReference)
      throws Exception {
    ResponseTest[] tests = {
      responseTest(
          response -> {},
          response -> assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200)),
      responseTest(
          response -> response.setStatusCode(HttpStatus.OK_200),
          response -> assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200)),
      responseTest(
          response -> response.setStatusCode(HttpStatus.IM_A_TEAPOT_418),
          response -> assertThat(response.getStatus()).isEqualTo(HttpStatus.IM_A_TEAPOT_418)),
      responseTest(
          response -> response.setStatusCode(HttpStatus.IM_A_TEAPOT_418, "Je suis une théière"),
          response -> {
            assertThat(response.getStatus()).isEqualTo(HttpStatus.IM_A_TEAPOT_418);
            // Reason string cannot be set by the application.
            assertThat(response.getReason()).isEqualTo(Code.IM_A_TEAPOT.getMessage());
          }),
      responseTest(
          response -> response.setContentType("application/noddy"),
          response -> assertThat(response.getMediaType()).isEqualTo("application/noddy")),
      responseTest(
          response -> {
            response.appendHeader("foo", "bar");
            response.appendHeader("blim", "blam");
            response.appendHeader("foo", "baz");
          },
          response -> {
            assertThat(response.getHeaders().getValuesList("foo")).containsExactly("bar", "baz");
            assertThat(response.getHeaders().getValuesList("blim")).containsExactly("blam");
          }),
      responseTest(
          response -> {
            response.setContentType("text/plain");
            try (BufferedWriter writer = response.getWriter()) {
              writer.write(TEST_BODY);
            }
          },
          response -> {
            assertThat(response.getMediaType()).isEqualTo("text/plain");
            assertThat(response.getContentAsString()).isEqualTo(TEST_BODY);
          }),
      responseTest(
          response -> {
            response.setContentType("application/octet-stream");
            try (OutputStream outputStream = response.getOutputStream()) {
              outputStream.write(RANDOM_BYTES);
            }
          },
          response -> {
            assertThat(response.getMediaType()).isEqualTo("application/octet-stream");
            assertThat(response.getContent()).isEqualTo(RANDOM_BYTES);
          }),
    };
    for (ResponseTest test : tests) {
      testReference.set(test.responseOperation);
      HttpClient httpClient = new HttpClient();
      httpClient.start();
      String uri = "http://localhost:" + serverPort;
      org.eclipse.jetty.client.Request request = httpClient.POST(uri);
      ContentResponse response = request.send();
      throwIfNotNull(exceptionReference.get());
      test.responseCheck.test(response);
    }
  }

  private static void throwIfNotNull(Throwable t) throws Exception {
    if (t != null) {
      if (t instanceof Error) {
        throw (Error) t;
      } else if (t instanceof Exception) {
        throw (Exception) t;
      } else {
        // Some kind of mutant Throwable that is neither an Exception nor an Error.
        throw new AssertionError(t);
      }
    }
  }
}
