package com.google.cloud.functions.invoker.runner;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
    String help = captureOutput(() -> {
      Optional<Invoker> invoker = Invoker.makeInvoker("--help");
      assertThat(invoker).isEmpty();
    });
    assertThat(help).contains("Usage:");
    assertThat(help).contains("--target");
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
    assertThat(invoker.get().getFunctionTarget()).isEqualTo("TestFunction.function");
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
  public void defaultJar() {
    Optional<Invoker> invoker = Invoker.makeInvoker();
    assertThat(invoker.get().getFunctionJarPath()).isEmpty();
  }

  @Test
  public void explicitJarViaEnvironment() {
    Map<String, String> env = Collections.singletonMap("FUNCTION_JAR", "/foo/bar/baz.jar");
    Optional<Invoker> invoker = Invoker.makeInvoker(env);
    assertThat(invoker.get().getFunctionJarPath()).hasValue("/foo/bar/baz.jar");
  }

  @Test
  public void explicitJarViaOption() {
    Optional<Invoker> invoker = Invoker.makeInvoker("--jar", "/foo/bar/baz.jar");
    assertThat(invoker.get().getFunctionJarPath()).hasValue("/foo/bar/baz.jar");
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
