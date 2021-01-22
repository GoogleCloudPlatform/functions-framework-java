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

package com.google.cloud.functions.invoker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.common.truth.Expect;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.http.HttpMessageFactory;
import io.cloudevents.jackson.JsonFormat;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

/**
 * Integration test that starts up a web server running the Function Framework and sends HTTP
 * requests to it.
 */
public class IntegrationTest {
  @Rule public final Expect expect = Expect.create();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public final TestName testName = new TestName();

  private static final String SERVER_READY_STRING = "Started ServerConnector";

  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

  private static String sampleLegacyEvent(File snoopFile) {
    return "{\n"
        + "  \"data\": {\n"
        + "    \"a\": 2,\n"
        + "    \"b\": 3,\n"
        + "    \"targetFile\": \"" + snoopFile + "\""
        + "  },\n"
        + "  \"context\": {\n"
        + "    \"eventId\": \"B234-1234-1234\",\n"
        + "    \"timestamp\": \"2018-04-05T17:31:00Z\",\n"
        + "    \"eventType\": \"com.example.someevent.new\",\n"
        + "    \"resource\": {\n"
        + "      \"service\":\"test-service\",\n"
        + "      \"name\":\"test-name\",\n"
        + "      \"type\":\"test-type\"\n"
        + "    }\n"
        + "  }\n"
        + "}";
  }

  private static CloudEvent sampleCloudEvent(File snoopFile) {
    return CloudEventBuilder.v1()
        .withId("B234-1234-1234")
        .withSource(URI.create("/source"))
        .withType("com.example.someevent.new")
        .withDataSchema(URI.create("/schema"))
        .withDataContentType("application/json")
        .withData(("{\"a\": 2, \"b\": 3, \"targetFile\": \"" + snoopFile + "\"}").getBytes(UTF_8))
        .withTime(OffsetDateTime.of(2018, 4, 5, 17, 31, 0, 0, ZoneOffset.UTC))
        .build();
  }

  private static JsonObject expectedCloudEventAttributes() {
    JsonObject attributes = new JsonObject();
    attributes.addProperty("datacontenttype", "application/json");
    attributes.addProperty("specversion", "1.0");
    attributes.addProperty("id", "B234-1234-1234");
    attributes.addProperty("source", "/source");
    attributes.addProperty("time", "2018-04-05T17:31Z");
    attributes.addProperty("type", "com.example.someevent.new");
    attributes.addProperty("dataschema", "/schema");
    return attributes;
  }

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
   * server, with the given {@link #requestContent()} ()} as the body of the POST, then we expect to
   * get back the given {@link #expectedResponseText()} in the body of the response.
   */
  @AutoValue
  abstract static class TestCase {

    abstract String url();

    abstract ContentProvider requestContent();

    abstract int expectedResponseCode();

    abstract Optional<String> expectedResponseText();

    abstract Optional<JsonObject> expectedJson();

    abstract Optional<String> expectedContentType();

    abstract Optional<String> expectedOutput();

    abstract Optional<String> httpContentType();

    abstract ImmutableMap<String, String> httpHeaders();

    abstract Optional<File> snoopFile();

    static Builder builder() {
      return new AutoValue_IntegrationTest_TestCase.Builder()
          .setUrl("/")
          .setRequestText("")
          .setExpectedResponseCode(HttpStatus.OK_200)
          .setExpectedResponseText("")
          .setHttpContentType("text/plain")
          .setHttpHeaders(ImmutableMap.of());
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setUrl(String x);

      abstract Builder setRequestContent(ContentProvider x);

      Builder setRequestText(String text) {
        return setRequestContent(new StringContentProvider(text));
      }

      abstract Builder setExpectedResponseCode(int x);

      abstract Builder setExpectedResponseText(String x);

      abstract Builder setExpectedResponseText(Optional<String> x);

      abstract Builder setExpectedContentType(String x);

      abstract Builder setExpectedOutput(String x);

      abstract Builder setExpectedJson(JsonObject x);

      abstract Builder setHttpContentType(String x);

      abstract Builder setHttpContentType(Optional<String> x);

      abstract Builder setHttpHeaders(ImmutableMap<String, String> x);

      abstract Builder setSnoopFile(File x);

      abstract TestCase build();
    }
  }

  private static String fullTarget(String nameWithoutPackage) {
    return "com.google.cloud.functions.invoker.testfunctions." + nameWithoutPackage;
  }

  private static final TestCase FAVICON_TEST_CASE =
      TestCase.builder()
          .setUrl("/favicon.ico?foo=bar")
          .setExpectedResponseCode(HttpStatus.NOT_FOUND_404)
          .setExpectedResponseText(Optional.empty())
          .build();

  private static final TestCase ROBOTS_TXT_TEST_CASE =
      TestCase.builder()
          .setUrl("/robots.txt?foo=bar")
          .setExpectedResponseCode(HttpStatus.NOT_FOUND_404)
          .setExpectedResponseText(Optional.empty())
          .build();

  @Test
  public void helloWorld() throws Exception {
    testHttpFunction(fullTarget("HelloWorld"),
        ImmutableList.of(
            TestCase.builder().setExpectedResponseText("hello\n").build(),
            FAVICON_TEST_CASE,
            ROBOTS_TXT_TEST_CASE));
  }

  @Test
  public void echo() throws Exception {
    String testText = "hello\nworld\n";
    testHttpFunction(
        fullTarget("Echo"),
        ImmutableList.of(
            TestCase.builder()
                .setRequestText(testText)
                .setExpectedResponseText(testText)
                .setExpectedContentType("text/plain")
                .build(),
            TestCase.builder()
                .setHttpContentType("application/octet-stream")
                .setRequestText(testText)
                .setExpectedResponseText(testText)
                .setExpectedContentType("application/octet-stream")
                .build()));
  }

  @Test
  public void echoUrl() throws Exception {
    String[] testUrls = {"/", "/foo/bar", "/?foo=bar&baz=buh", "/foo?bar=baz"};
    List<TestCase> testCases = Arrays.stream(testUrls)
        .map(url -> TestCase.builder().setUrl(url).setExpectedResponseText(url + "\n").build())
        .collect(toList());
    testHttpFunction(fullTarget("EchoUrl"), testCases);
  }

  @Test
  public void stackDriverLogging() throws Exception {
    String simpleExpectedOutput =
        "{\"severity\": \"INFO\", "
            + "\"logging.googleapis.com/sourceLocation\": "
            + "{\"file\": \"com/google/cloud/functions/invoker/testfunctions/Log.java\","
            + " \"method\": \"service\"},"
            + " \"message\": \"blim\"}";
    TestCase simpleTestCase =
        TestCase.builder()
            .setUrl("/?message=blim")
            .setExpectedOutput(simpleExpectedOutput)
            .build();
    String quotingExpectedOutput = "\"message\": \"foo\\nbar\\\"";
    TestCase quotingTestCase =
        TestCase.builder()
            .setUrl("/?message=" + URLEncoder.encode("foo\nbar\"", "UTF-8"))
            .setExpectedOutput(quotingExpectedOutput)
            .build();
    String exceptionExpectedOutput =
        "{\"severity\": \"ERROR\", "
            + "\"logging.googleapis.com/sourceLocation\": "
            + "{\"file\": \"com/google/cloud/functions/invoker/testfunctions/Log.java\", "
            + "\"method\": \"service\"}, "
            + "\"message\": \"oops\\njava.lang.Exception: disaster\\n"
            + "	at com.google.cloud.functions.invoker.testfunctions.Log.service(Log.java:";
    TestCase exceptionTestCase =
        TestCase.builder()
            .setUrl("/?message=oops&level=severe&exception=disaster")
            .setExpectedOutput(exceptionExpectedOutput)
            .build();
    testHttpFunction(
        fullTarget("Log"), ImmutableList.of(simpleTestCase, quotingTestCase, exceptionTestCase));
  }

  @Test
  public void background() throws Exception {
    backgroundTest("BackgroundSnoop");
  }

  @Test
  public void typedBackground() throws Exception {
    backgroundTest("TypedBackgroundSnoop");
  }

  private void backgroundTest(String target) throws Exception {
    File snoopFile = snoopFile();
    String gcfRequestText = sampleLegacyEvent(snoopFile);
    JsonObject expectedJson = new Gson().fromJson(gcfRequestText, JsonObject.class);
    // We don't currently put anything in the attributes() map for legacy events.
    expectedJson.add("attributes", new JsonObject());
    TestCase gcfTestCase = TestCase.builder()
        .setRequestText(gcfRequestText)
        .setSnoopFile(snoopFile)
        .setExpectedJson(expectedJson)
        .build();

    // A CloudEvent using the "structured content mode", where both the metadata and the payload
    // are in the body of the HTTP request.
    EventFormat jsonFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
    String cloudEventRequestText = new String(jsonFormat.serialize(sampleCloudEvent(snoopFile)), UTF_8);
    // For CloudEvents, we don't currently populate Context#getResource with anything interesting,
    // so we excise that from the expected text we would have with legacy events.
    JsonObject cloudEventExpectedJson = new Gson().fromJson(gcfRequestText, JsonObject.class);
    cloudEventExpectedJson.getAsJsonObject("context").add("resource", new JsonObject());
    cloudEventExpectedJson.add("attributes", expectedCloudEventAttributes());
    TestCase cloudEventsStructuredTestCase = TestCase.builder()
        .setSnoopFile(snoopFile)
        .setRequestText(cloudEventRequestText)
        .setHttpContentType("application/cloudevents+json; charset=utf-8")
        .setExpectedJson(cloudEventExpectedJson)
        .build();

    // A CloudEvent using the "binary content mode", where the metadata is in HTTP headers and the
    // payload is the body of the HTTP request.
    Map<String, String> headers = new TreeMap<>();
    AtomicReference<byte[]> bodyRef = new AtomicReference<>();
    HttpMessageFactory.createWriter(headers::put, bodyRef::set)
        .writeBinary(sampleCloudEvent(snoopFile));
    TestCase cloudEventsBinaryTestCase = TestCase.builder()
        .setSnoopFile(snoopFile)
        .setRequestText(new String(bodyRef.get(), UTF_8))
        .setHttpContentType(headers.get("Content-Type"))
        .setHttpHeaders(ImmutableMap.copyOf(headers))
        .setExpectedJson(cloudEventExpectedJson)
        .build();

    backgroundTest(
        SignatureType.BACKGROUND,
        fullTarget(target),
        ImmutableList.of(gcfTestCase, cloudEventsStructuredTestCase, cloudEventsBinaryTestCase));
  }

  /** Tests a CloudEvent being handled by a CloudEvent handler (no translation to or from legacy). */
  @Test
  public void nativeCloudEvent() throws Exception {
    File snoopFile = snoopFile();
    CloudEvent cloudEvent = sampleCloudEvent(snoopFile);
    EventFormat jsonFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
    String cloudEventJson = new String(jsonFormat.serialize(cloudEvent), UTF_8);

    // A CloudEvent using the "structured content mode", where both the metadata and the payload
    // are in the body of the HTTP request.
    JsonObject cloudEventJsonObject = new Gson().fromJson(cloudEventJson, JsonObject.class);
    TestCase cloudEventsStructuredTestCase = TestCase.builder()
        .setSnoopFile(snoopFile)
        .setRequestText(cloudEventJson)
        .setHttpContentType("application/cloudevents+json; charset=utf-8")
        .setExpectedJson(cloudEventJsonObject)
        .build();

    // A CloudEvent using the "binary content mode", where the metadata is in HTTP headers and the
    // payload is the body of the HTTP request.
    Map<String, String> headers = new TreeMap<>();
    AtomicReference<byte[]> bodyRef = new AtomicReference<>();
    HttpMessageFactory.createWriter(headers::put, bodyRef::set)
        .writeBinary(sampleCloudEvent(snoopFile));
    TestCase cloudEventsBinaryTestCase = TestCase.builder()
        .setSnoopFile(snoopFile)
        .setRequestText(new String(bodyRef.get(), UTF_8))
        .setHttpContentType(headers.get("Content-Type"))
        .setHttpHeaders(ImmutableMap.copyOf(headers))
        .setExpectedJson(cloudEventJsonObject)
        .build();

    backgroundTest(
        SignatureType.CLOUD_EVENT,
        fullTarget("CloudEventSnoop"),
        ImmutableList.of(cloudEventsStructuredTestCase, cloudEventsBinaryTestCase));
  }

  @Test
  public void nested() throws Exception {
    String testText = "sic transit gloria mundi";
    testHttpFunction(
        fullTarget("Nested.Echo"),
        ImmutableList.of(
            TestCase.builder().setRequestText(testText).setExpectedResponseText(testText).build()));
  }

  @Test
  public void packageless() throws Exception {
    testHttpFunction("PackagelessHelloWorld",
        ImmutableList.of(TestCase.builder().setExpectedResponseText("hello, world\n").build()));
  }

  @Test
  public void multipart() throws Exception {
    MultiPartContentProvider multiPartProvider = new MultiPartContentProvider();
    byte[] bytes = new byte[17];
    multiPartProvider.addFieldPart("bytes", new BytesContentProvider(bytes), new HttpFields());
    String string = "1234567890";
    multiPartProvider.addFieldPart("string", new StringContentProvider(string), new HttpFields());
    String expectedResponse = "part bytes type application/octet-stream length 17\n"
        + "part string type text/plain;charset=UTF-8 length 10\n";
    testHttpFunction(
        fullTarget("Multipart"),
        ImmutableList.of(
            TestCase.builder()
                .setHttpContentType(Optional.empty())
                .setRequestContent(multiPartProvider)
                .setExpectedResponseText(expectedResponse)
                .build()));
  }

  private File snoopFile() throws IOException {
    return temporaryFolder.newFile(testName.getMethodName() + ".txt");
  }

  /** Any runtime class that user code shouldn't be able to see. */
  private static final Class<?> INTERNAL_CLASS = CloudFunctionsContext.class;

  private String functionJarString() throws IOException {
    Path functionJarTargetDir = Paths.get("../testfunction/target");
    Pattern functionJarPattern =
        Pattern.compile("java-function-invoker-testfunction-.*-tests\\.jar");
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
   * Tests that if we launch an HTTP function with {@code --classpath}, then the function code
   * cannot see the classes from the runtime. This is allows us to avoid conflicts between versions
   * of libraries that we use in the runtime and different versions of the same libraries that the
   * function might use.
   */
  @Test
  public void classpathOptionHttp() throws Exception {
    TestCase testCase = TestCase.builder()
        .setUrl("/?class=" + INTERNAL_CLASS.getName())
        .setExpectedResponseText("OK")
        .build();
    testFunction(
        SignatureType.HTTP,
        "com.example.functionjar.Foreground",
        ImmutableList.of("--classpath", functionJarString()),
        ImmutableList.of(testCase));
  }

  /** Like {@link #classpathOptionHttp} but for background functions. */
  @Test
  public void classpathOptionBackground() throws Exception {
    Gson gson = new Gson();
    URL resourceUrl = getClass().getResource("/adder_gcf_ga_event.json");
    assertThat(resourceUrl).isNotNull();
    String originalJson = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
    JsonObject json = gson.fromJson(originalJson, JsonObject.class);
    JsonObject jsonData = json.getAsJsonObject("data");
    jsonData.addProperty("class", INTERNAL_CLASS.getName());
    testFunction(
        SignatureType.BACKGROUND,
        "com.example.functionjar.Background",
        ImmutableList.of("--classpath", functionJarString()),
        ImmutableList.of(TestCase.builder().setRequestText(json.toString()).build()));
  }

  // In these tests, we test a number of different functions that express the same functionality
  // in different ways. Each function is invoked with a complete HTTP body that looks like a real
  // event. We start with a fixed body and insert into its JSON an extra property that tells the
  // function where to write what it received. We have to do this since background functions, by
  // design, don't return a value.
  private void backgroundTest(
      SignatureType signatureType, String functionTarget, List<TestCase> testCases)
      throws Exception {
    for (TestCase testCase : testCases) {
      File snoopFile = testCase.snoopFile().get();
      snoopFile.delete();
      testFunction(signatureType, functionTarget, ImmutableList.of(), ImmutableList.of(testCase));
      String snooped = new String(Files.readAllBytes(snoopFile.toPath()), StandardCharsets.UTF_8);
      Gson gson = new Gson();
      JsonObject snoopedJson = gson.fromJson(snooped, JsonObject.class);
      JsonObject expectedJson = testCase.expectedJson().get();
      expect.withMessage(
              "Testing %s with %s\nGOT %s\nNOT %s", functionTarget, testCase, snoopedJson, expectedJson)
          .that(snoopedJson).isEqualTo(expectedJson);
    }
  }

  private void checkSnoopFile(TestCase testCase) throws IOException {
    File snoopFile = testCase.snoopFile().get();
    JsonObject expectedJson = testCase.expectedJson().get();
    String snooped = new String(Files.readAllBytes(snoopFile.toPath()), StandardCharsets.UTF_8);
    Gson gson = new Gson();
    JsonObject snoopedJson = gson.fromJson(snooped, JsonObject.class);
    expect.withMessage("Testing with %s", testCase).that(snoopedJson).isEqualTo(expectedJson);
  }

  private void testHttpFunction(String target, List<TestCase> testCases) throws Exception {
    testFunction(SignatureType.HTTP, target, ImmutableList.of(), testCases);
  }

  private void testFunction(
      SignatureType signatureType,
      String target,
      ImmutableList<String> extraArgs,
      List<TestCase> testCases) throws Exception {
    ServerProcess serverProcess = startServer(signatureType, target, extraArgs);
    try {
      HttpClient httpClient = new HttpClient();
      httpClient.start();
      for (TestCase testCase : testCases) {
        testCase.snoopFile().ifPresent(File::delete);
        String uri = "http://localhost:" + serverPort + testCase.url();
        Request request = httpClient.POST(uri);
        testCase.httpContentType().ifPresent(
            contentType -> request.header(HttpHeader.CONTENT_TYPE, contentType));
        testCase.httpHeaders().forEach((header, value) -> request.header(header, value));
        request.content(testCase.requestContent());
        ContentResponse response = request.send();
        expect
            .withMessage("Response to %s is %s %s", uri, response.getStatus(), response.getReason())
            .that(response.getStatus()).isEqualTo(testCase.expectedResponseCode());
        testCase.expectedResponseText()
            .ifPresent(text -> expect.that(response.getContentAsString()).isEqualTo(text));
        testCase.expectedContentType()
            .ifPresent(type -> expect.that(response.getMediaType()).isEqualTo(type));
        if (testCase.snoopFile().isPresent()) {
          checkSnoopFile(testCase);
        }
      }
    } finally {
      serverProcess.close();
    }
    for (TestCase testCase : testCases) {
      testCase.expectedOutput()
          .ifPresent(output -> expect.that(serverProcess.output()).contains(output));
    }
    // Wait for the output monitor task to terminate. If it threw an exception, we will get an
    // ExecutionException here.
    serverProcess.outputMonitorResult().get();
  }

  private enum SignatureType {
    HTTP("http"),
    BACKGROUND("event"),
    CLOUD_EVENT("cloudevent");

    private final String name;

    SignatureType(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static class ServerProcess implements AutoCloseable {
    private final Process process;
    private final Future<?> outputMonitorResult;
    private final StringBuilder output;

    ServerProcess(Process process, Future<?> outputMonitorResult, StringBuilder output) {
      this.process = process;
      this.outputMonitorResult = outputMonitorResult;
      this.output = output;
    }

    Process process() {
      return process;
    }

    Future<?> outputMonitorResult() {
      return outputMonitorResult;
    }

    String output() {
      synchronized (output) {
        return output.toString();
      }
    }

    @Override
    public void close() {
      process().destroy();
      try {
        process().waitFor();
      } catch (InterruptedException e) {
        // Should not happen.
      }
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
    StringBuilder output = new StringBuilder();
    Future<?> outputMonitorResult = EXECUTOR.submit(
        () -> monitorOutput(serverProcess.getInputStream(), ready, output));
    boolean serverReady = ready.await(5, TimeUnit.SECONDS);
    if (!serverReady) {
      serverProcess.destroy();
      throw new AssertionError("Server never became ready");
    }
    return new ServerProcess(serverProcess, outputMonitorResult, output);
  }

  private void monitorOutput(
      InputStream processOutput, CountDownLatch ready, StringBuilder output) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains(SERVER_READY_STRING)) {
          ready.countDown();
        }
        System.out.println(line);
        synchronized (output) {
          output.append(line).append('\n');
        }
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
