package com.example.functionjar;

import com.google.cloud.functions.TypedFunction;

public class Typed implements TypedFunction<NameConcatRequest, NameConcatResponse> {

  @Override
  public NameConcatResponse handle(NameConcatRequest arg) throws Exception {
    return new NameConcatResponse().setFullName(arg.firstName + arg.lastName);
  }
}

class NameConcatRequest {
  String firstName;
  String lastName;
}

class NameConcatResponse {
  String fullName;

  NameConcatResponse setFullName(String fullName) {
    this.fullName = fullName;
    return this;
  }
}
