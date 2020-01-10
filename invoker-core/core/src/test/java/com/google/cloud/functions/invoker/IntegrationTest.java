package com.google.cloud.functions.invoker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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

  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

  private static int serverPort;

  /**
   * Each test method will start up a server on the same port, make one or more HTTP requests to
   * that port, then kill the server. So the port should be free when the next test method runs.
   */
  @BeforeClass
  public static void allocateServerPort() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      serverPort = serverSocket.getLocalPort();
    }
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

  private static String fullTarget(String nameWithoutPackage) {
    return "com.google.cloud.functions.invoker.testfunctions." + nameWithoutPackage;
  }

  @Test
  public void helloWorld() throws Exception {
    testHttpFunction(fullTarget("HelloWorld.helloWorld"),
        TestCase.builder().setExpectedResponseText("hello\n").build());
  }

  @Test
  public void newHelloWorld() throws Exception {
    testHttpFunction(fullTarget("NewHelloWorld"),
        TestCase.builder().setExpectedResponseText("hello\n").build());
  }

  @Test
  public void echo() throws Exception {
    String testText = "hello\nworld\n";
    testHttpFunction(fullTarget("Echo.echo"),
        TestCase.builder().setRequestText(testText).setExpectedResponseText(testText).build());
  }

  @Test
  public void newEcho() throws Exception {
    String testText = "hello\nworld\n";
    testHttpFunction(fullTarget("NewEcho"),
        TestCase.builder().setRequestText(testText).setExpectedResponseText(testText).build());
  }

  @Test
  public void echoUrl() throws Exception {
    String[] testUrls = {"/", "/foo/bar", "/?foo=bar&baz=buh", "/foo?bar=baz"};
    TestCase[] testCases = Arrays.stream(testUrls)
        .map(url -> TestCase.builder().setUrl(url).setExpectedResponseText(url + "\n").build())
        .toArray(TestCase[]::new);
    testHttpFunction(fullTarget("EchoUrl.echoUrl"), testCases);
  }

  @Test
  public void newEchoUrl() throws Exception {
    String[] testUrls = {"/", "/foo/bar", "/?foo=bar&baz=buh", "/foo?bar=baz"};
    TestCase[] testCases = Arrays.stream(testUrls)
        .map(url -> TestCase.builder().setUrl(url).setExpectedResponseText(url + "\n").build())
        .toArray(TestCase[]::new);
    testHttpFunction(fullTarget("NewEchoUrl"), testCases);
  }

  @Test
  public void background() throws Exception {
    backgroundTest(fullTarget("BackgroundSnoop.snoop"));
  }

  @Test
  public void newBackground() throws Exception {
    backgroundTest(fullTarget("NewBackgroundSnoop"));
  }

  @Test
  public void newTypedBackground() throws Exception {
    backgroundTest(fullTarget("NewTypedBackgroundSnoop"));
  }

  @Test
  public void nested() throws Exception {
    String testText = "sic transit gloria mundi";
    testHttpFunction(fullTarget("Nested.Echo"),
        TestCase.builder().setRequestText(testText).setExpectedResponseText(testText).build());
  }

  @Test
  public void packageless() throws Exception {
    testHttpFunction("PackagelessHelloWorld",
        TestCase.builder().setExpectedResponseText("hello, world\n").build());
  }

  /** Any runtime class that user code shouldn't be able to see. */
  private static final Class<?> INTERNAL_CLASS = CloudFunction.class;

  private String functionJarString() throws IOException {
    Path functionJarTargetDir = Paths.get("../functionjar/target");
    Pattern functionJarPattern =
        Pattern.compile("java-function-invoker-core-functionjar-.*-tests\\.jar");
    List<Path> functionJars = Files.list(functionJarTargetDir)
        .map(path -> path.getFileName().toString())
        .filter(s -> functionJarPattern.matcher(s).matches())
        .map(s -> functionJarTargetDir.resolve(s))
        .collect(toList());
    assertWithMessage("Number of jars in %s matching %s", functionJarTargetDir, functionJarPattern)
        .that(functionJars).hasSize(1);
    return Iterables.getOnlyElement(functionJars).toString();
  }

  /**
   * Tests that if we launch an HTTP function with {@code -jar}, then the function code cannot
   * see the classes from the runtime. This is allows us to avoid conflicts between versions of
   * libraries that we use in the runtime and different versions of the same libraries that the
   * function might use.
   */
  @Test
  public void jarOptionHttp() throws Exception {
    testHttpFunction("com.example.functionjar.Foreground",
        ImmutableList.of("-jar", functionJarString()),
        TestCase.builder()
            .setUrl("/?class=" + INTERNAL_CLASS.getName())
            .setExpectedResponseText("OK")
            .build());
  }

  /** Like {@link #jarOptionHttp} but for background functions. */
  @Test
  public void jarOptionBackground() throws Exception {
    Gson gson = new Gson();
    URL resourceUrl = getClass().getResource("/adder_gcf_ga_event.json");
    assertThat(resourceUrl).isNotNull();
    String originalJson = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
    JsonObject json = gson.fromJson(originalJson, JsonObject.class);
    JsonObject jsonData = json.getAsJsonObject("data");
    jsonData.addProperty("class", INTERNAL_CLASS.getName());
    testBackgroundFunction("com.example.functionjar.Background",
        ImmutableList.of("-jar", functionJarString()),
        TestCase.builder().setRequestText(json.toString()).build());
  }

  // In these tests, we test a number of different functions that express the same functionality
  // in different ways. Each function is invoked with a complete HTTP body that looks like a real
  // event. We start with a fixed body and insert into its JSON an extra property that tells the
  // function where to write what it received. We have to do this since background functions, by
  // design, don't return a value.
  private void backgroundTest(String functionTarget) throws Exception {
    Gson gson = new Gson();
    URL resourceUrl = getClass().getResource("/adder_gcf_ga_event.json");
    assertThat(resourceUrl).isNotNull();
    File snoopFile = File.createTempFile("FunctionsIntegrationTest", ".txt");
    snoopFile.deleteOnExit();
    String originalJson = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
    JsonObject json = gson.fromJson(originalJson, JsonObject.class);
    JsonObject jsonData = json.getAsJsonObject("data");
    jsonData.addProperty("targetFile", snoopFile.toString());
    testBackgroundFunction(functionTarget,
        TestCase.builder().setRequestText(json.toString()).build());
    String snooped = new String(Files.readAllBytes(snoopFile.toPath()), StandardCharsets.UTF_8);
    JsonObject snoopedJson = gson.fromJson(snooped, JsonObject.class);
    assertThat(snoopedJson).isEqualTo(json);
  }

  private void testHttpFunction(String target, TestCase... testCases) throws Exception {
    testHttpFunction(target, ImmutableList.of(), testCases);
  }

  private void testHttpFunction(
      String target, ImmutableList<String> extraArgs, TestCase... testCases) throws Exception {
    testFunction(SignatureType.HTTP, target, extraArgs, testCases);
  }

  private void testBackgroundFunction(String classAndMethod, TestCase... testCases)
      throws Exception {
    testBackgroundFunction(classAndMethod, ImmutableList.of(), testCases);
  }
  private void testBackgroundFunction(
      String classAndMethod, ImmutableList<String> extraArgs, TestCase... testCases)
      throws Exception {
    testFunction(SignatureType.BACKGROUND, classAndMethod, extraArgs, testCases);
  }

  private void testFunction(
      SignatureType signatureType,
      String target,
      ImmutableList<String> extraArgs,
      TestCase... testCases) throws Exception {
    ServerProcess serverProcess = startServer(signatureType, target, extraArgs);
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
      serverProcess.close();
    }
    // Wait for the output monitor task to terminate. If it threw an exception, we will get an
    // ExecutionException here.
    serverProcess.outputMonitorResult().get();
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

  @AutoValue
  abstract static class ServerProcess implements AutoCloseable {
    abstract Process process();
    abstract Future<?> outputMonitorResult();

    static ServerProcess of(Process process, Future<?> outputMonitorResult) {
      return new AutoValue_IntegrationTest_ServerProcess(process, outputMonitorResult);
    }

    @Override
    public void close() {
      process().destroy();
    }
  }

  private ServerProcess startServer(
      SignatureType signatureType, String target, ImmutableList<String> extraArgs)
      throws IOException, InterruptedException {
    File javaHome = new File(System.getProperty("java.home"));
    assertThat(javaHome.exists()).isTrue();
    File javaBin = new File(javaHome, "bin");
    File javaCommand = new File(javaBin, "java");
    assertThat(javaCommand.exists()).isTrue();
    String myClassPath = System.getProperty("java.class.path");
    assertThat(myClassPath).isNotNull();
    ImmutableList<String> command = ImmutableList.<String>builder()
        .add(javaCommand.toString(), "-classpath", myClassPath, Invoker.class.getName())
        .addAll(extraArgs)
        .build();
    ProcessBuilder processBuilder = new ProcessBuilder()
        .command(command)
        .redirectErrorStream(true);
    Map<String, String> environment = ImmutableMap.of("PORT", String.valueOf(serverPort),
        "K_SERVICE", "test-function",
        "FUNCTION_SIGNATURE_TYPE", signatureType.toString(),
        "FUNCTION_TARGET", target);
    processBuilder.environment().putAll(environment);
    Process serverProcess = processBuilder.start();
    CountDownLatch ready = new CountDownLatch(1);
    Future<?> outputMonitorResult = EXECUTOR.submit(
        () -> monitorOutput(serverProcess.getInputStream(), ready));
    boolean serverReady = ready.await(5, TimeUnit.SECONDS);
    if (!serverReady) {
      serverProcess.destroy();
      throw new AssertionError("Server never became ready");
    }
    return ServerProcess.of(serverProcess, outputMonitorResult);
  }

  private void monitorOutput(InputStream processOutput, CountDownLatch ready) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains(SERVER_READY_STRING)) {
          ready.countDown();
        }
        System.out.println(line);
        if (line.contains("WARNING")) {
          throw new AssertionError("Found warning in server output:\n" + line);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new UncheckedIOException(e);
    }
  }
}
