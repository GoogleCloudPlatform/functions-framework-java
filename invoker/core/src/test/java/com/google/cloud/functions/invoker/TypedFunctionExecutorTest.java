package com.google.cloud.functions.invoker;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.functions.TypedFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TypedFunctionExecutorTest {
  private static class NameConcatRequest {
    String firstName;
    String lastName;
  }

  private static class NameConcatResponse {
    String fullName;
  }

  private static class NameConcatFunction
      implements TypedFunction<NameConcatRequest, NameConcatResponse> {
    @Override
    public NameConcatResponse apply(NameConcatRequest arg) throws Exception {
      NameConcatResponse resp = new NameConcatResponse();
      resp.fullName = arg.firstName + arg.lastName;
      return resp;
    }
  }

  @Test
  public void canDetermineTypeArgument() {
    assertThat(TypedFunctionExecutor.handlerTypeArgument(NameConcatFunction.class))
        .hasValue(NameConcatRequest.class);
  }
}
