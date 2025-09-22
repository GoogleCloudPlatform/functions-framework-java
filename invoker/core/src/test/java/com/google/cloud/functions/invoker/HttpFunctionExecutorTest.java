package com.google.cloud.functions.invoker;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HttpFunctionExecutorTest {
  private static ClassLoader customClassLoader =
      new ClassLoader(ClassLoader.getSystemClassLoader()) {};

  public static class ClassLoaderVerifier implements HttpFunction {
    public ClassLoaderVerifier() {
      assertThat(Thread.currentThread().getContextClassLoader())
          .isNotSameInstanceAs(customClassLoader);
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  @Test
  public void usesCorrectClassLoaderOverride() {
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(customClassLoader);
    HttpFunctionExecutor.forClass(ClassLoaderVerifier.class);
    Thread.currentThread().setContextClassLoader(oldClassLoader);
  }
}
