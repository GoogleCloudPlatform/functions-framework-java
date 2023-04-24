package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.TypedFunction;

public class TypedVoid implements TypedFunction<Request, Void> {
  @Override
  public Void apply(Request arg) throws Exception {
    return null;
  }
}

class Request {}
