package com.google.cloud.functions.invoker.runner;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.joining;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InvokerTest {
  @Test
  public void help() throws IOException {
    String help =
        captureOutput(
            () -> {
              Optional<Invoker> invoker = Invoker.makeInvoker("--help");
              assertThat(invoker).isEmpty();
            });
    assertThat(help).contains("Usage:");
    assertThat(help).contains("--target");
    assertThat(help).containsMatch("separated\\s+by\\s+'" + File.pathSeparator + "'");
  }

  @Test
  public void defaultPort() {
    Optional<Invoker> invoker = Invoker.makeInvoker();
    assertThat(invoker.get().getPort()).isEqualTo(8080);
  }

  @Test
  public void explicitPort() {
    Optional<Invoker> invoker = Invoker.makeInvoker("--port", "1234");
    assertThat(invoker.get().getPort()).isEqualTo(1234);
  }

  @Test
  public void defaultTarget() {
    Optional<Invoker> invoker = Invoker.makeInvoker();
    assertThat(invoker.get().getFunctionTarget()).isEqualTo("Function");
  }

  @Test
  public void explicitTarget() {
    Optional<Invoker> invoker = Invoker.makeInvoker("--target", "com.example.MyFunction");
    assertThat(invoker.get().getFunctionTarget()).isEqualTo("com.example.MyFunction");
  }

  @Test
  public void defaultSignatureType() {
    Optional<Invoker> invoker = Invoker.makeInvoker();
    assertThat(invoker.get().getFunctionSignatureType()).isNull();
  }

  @Test
  public void explicitSignatureType() {
    Map<String, String> env = Collections.singletonMap("FUNCTION_SIGNATURE_TYPE", "http");
    Optional<Invoker> invoker = Invoker.makeInvoker(env);
    assertThat(invoker.get().getFunctionSignatureType()).isEqualTo("http");
  }

  @Test
  public void defaultClasspath() {
    Optional<Invoker> invoker = Invoker.makeInvoker();
    assertThat(invoker.get().getClass().getClassLoader())
        .isSameInstanceAs(Invoker.class.getClassLoader());
  }

  private static final String FAKE_CLASSPATH =
      "/foo/bar/baz.jar" + File.pathSeparator + "/some/directory";

  @Test
  public void explicitClasspathViaEnvironment() {
    Map<String, String> env = Collections.singletonMap("FUNCTION_CLASSPATH", FAKE_CLASSPATH);
    Optional<Invoker> invoker = Invoker.makeInvoker(env);
    assertThat(invokerClasspath(invoker.get())).isEqualTo(FAKE_CLASSPATH);
  }

  @Test
  public void explicitClasspathViaOption() {
    Optional<Invoker> invoker = Invoker.makeInvoker("--classpath", FAKE_CLASSPATH);
    assertThat(invokerClasspath(invoker.get())).isEqualTo(FAKE_CLASSPATH);
  }

  private static String invokerClasspath(Invoker invoker) {
    URLClassLoader urlClassLoader = (URLClassLoader) invoker.getFunctionClassLoader();
    return Arrays.stream(urlClassLoader.getURLs())
        .map(URL::getPath)
        .collect(joining(File.pathSeparator));
  }

  @Test
  public void classpathToUrls() throws Exception {
    String classpath =
        "../testfunction/target/test-classes" + File.pathSeparator + "../testfunction/target/lib/*";
    URL[] urls = Invoker.classpathToUrls(classpath);
    assertWithMessage(Arrays.toString(urls)).that(urls.length).isGreaterThan(2);
    File classesDir = new File(urls[0].toURI());
    assertWithMessage(classesDir.toString()).that(classesDir.isDirectory()).isTrue();
    for (int i = 1; i < urls.length; i++) {
      URL url = urls[i];
      assertThat(url.toString()).endsWith(".jar");
      assertWithMessage(url.toString()).that(new File(url.toURI()).isFile()).isTrue();
    }
  }

  private static String captureOutput(Runnable operation) throws IOException {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream byteCapture = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(byteCapture)) {
      System.setOut(capture);
      System.setErr(capture);
      operation.run();
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
    return new String(byteCapture.toByteArray(), StandardCharsets.UTF_8);
  }
}
