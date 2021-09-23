package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;

public class ExceptionBackground implements RawBackgroundFunction {
  @Override
  public void accept(String json, Context context) {
    throw new RuntimeException("exception thrown for test");
  }
}
