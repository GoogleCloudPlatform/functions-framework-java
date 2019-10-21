package com.google.cloud.functions.invoker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.auto.value.AutoValue;
import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration test that starts up a web server running the Function Framework and sends HTTP
 * requests to it.
 */
public class IntegrationTest {

  private static final String SERVER_READY_STRING = "Started ServerConnector";

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
   * Description of a test case. When we send an HTTP POST to the given {@link #url()} in the
   * server, with the given {@link #requestText()} as the body of the POST, then we expect to get
   * back the given {@link #expectedResponseText()} in the body of the response.
   */
  @AutoValue
  abstract static class TestCase {

    abstract String url();

    abstract String requestText();

    abstract String expectedResponseText();

    static Builder builder() {
      return new AutoValue_IntegrationTest_TestCase.Builder()
          .setUrl("/")
          .setRequestText("")
          .setExpectedResponseText("");
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setUrl(String x);

      abstract Builder setRequestText(String x);

      abstract Builder setExpectedResponseText(String x);

      abstract TestCase build();
    }
  }

  @Test
  public void helloWorld() throws Exception {
    testHttpFunction("HelloWorld.helloWorld",
        TestCase.builder().setExpectedResponseText("hello\n").build());
  }

  @Test
  public void echo() throws Exception {
    String testText = "hello\nworld\n";
    testHttpFunction("Echo.echo",
        TestCase.builder().setRequestText(testText).setExpectedResponseText(testText).build());
  }

  @Test
  public void echoUrl() throws Exception {
    String[] testUrls = {"/", "/foo/bar", "/?foo=bar&baz=buh", "/foo?bar=baz"};
    TestCase[] testCases = Arrays.stream(testUrls)
        .map(url -> TestCase.builder().setUrl(url).setExpectedResponseText(url + "\n").build())
        .toArray(TestCase[]::new);
    testHttpFunction("EchoUrl.echoUrl", testCases);
  }

  @Test
  public void background() throws Exception {
    URL resourceUrl = getClass().getResource("/adder_gcf_ga_event.json");
    assertThat(resourceUrl).isNotNull();
    File snoopFile = File.createTempFile("FunctionsIntegrationTest", ".txt");
    snoopFile.deleteOnExit();
    String originalJson = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
    JsonObject json = new JsonParser().parse(originalJson).getAsJsonObject();
    JsonObject jsonData = json.getAsJsonObject("data");
    jsonData.addProperty("targetFile", snoopFile.toString());
    testBackgroundFunction("BackgroundSnoop.snoop",
        TestCase.builder().setRequestText(json.toString()).build());
    String snooped = Files.asCharSource(snoopFile, StandardCharsets.UTF_8).read();
    JsonObject snoopedJson = new JsonParser().parse(snooped).getAsJsonObject();
    assertThat(snoopedJson).isEqualTo(json);
  }

  private void testHttpFunction(String classAndMethod, TestCase... testCases) throws Exception {
    testFunction(SignatureType.HTTP, classAndMethod, testCases);
  }

  private void testBackgroundFunction(String classAndMethod, TestCase... testCases)
      throws Exception {
    testFunction(SignatureType.BACKGROUND, classAndMethod, testCases);
  }

  private void testFunction(SignatureType signatureType, String classAndMethod,
      TestCase... testCases) throws Exception {
    Process server = startServer(signatureType, classAndMethod);
    try {
      HttpClient httpClient = new HttpClient();
      httpClient.start();
      for (TestCase testCase : testCases) {
        String uri = "http://localhost:" + serverPort + testCase.url();
        Request request = httpClient.POST(uri);
        request.header(HttpHeader.CONTENT_TYPE, "text/plain");
        request.content(new StringContentProvider(testCase.requestText()));
        ContentResponse response = request.send();
        assertWithMessage("Response to %s is %s %s", uri, response.getStatus(),
            response.getReason())
            .that(response.getStatus()).isEqualTo(HttpStatus.OK_200);
        assertThat(response.getContentAsString()).isEqualTo(testCase.expectedResponseText());
      }
    } finally {
      server.destroy();
      server.waitFor();
    }
  }

  private enum SignatureType {
    HTTP("http"),
    BACKGROUND("event");

    private final String name;

    SignatureType(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private Process startServer(SignatureType signatureType, String classAndMethod)
      throws IOException, InterruptedException {
    String fullMethodName = "com.google.cloud.functions.invoker.testfunctions." + classAndMethod;
    File javaHome = new File(System.getProperty("java.home"));
    assertThat(javaHome.exists()).isTrue();
    File javaBin = new File(javaHome, "bin");
    File javaCommand = new File(javaBin, "java");
    assertThat(javaCommand.exists()).isTrue();
    String myClassPath = System.getProperty("java.class.path");
    assertThat(myClassPath).isNotNull();
    String[] command = {
        javaCommand.toString(), "-classpath", myClassPath, Invoker.class.getName(),
    };
    ProcessBuilder processBuilder = new ProcessBuilder()
        .command(command)
        .redirectErrorStream(true);
    Map<String, String> environment = ImmutableMap.of(
        "PORT", String.valueOf(serverPort),
        "K_SERVICE", "test-function",
        "FUNCTION_SIGNATURE_TYPE", signatureType.toString(),
        "FUNCTION_TARGET", fullMethodName);
    processBuilder.environment().putAll(environment);
    Process serverProcess = processBuilder.start();
    CountDownLatch ready = new CountDownLatch(1);
    new Thread(() -> monitorOutput(serverProcess.getInputStream(), ready)).start();
    ready.await(5, TimeUnit.SECONDS);
    return serverProcess;
  }

  private void monitorOutput(InputStream processOutput, CountDownLatch ready) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains(SERVER_READY_STRING)) {
          ready.countDown();
        }
        System.out.println(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new UncheckedIOException(e);
    }
  }
}
