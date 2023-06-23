package com.google.cloud.functions.conformance;

import com.google.cloud.functions.TypedFunction;
import com.google.gson.JsonObject;

public class TypedConformanceFunction implements TypedFunction<JsonObject, ConformanceResponse> {
  @Override
  public ConformanceResponse apply(JsonObject req) throws Exception {
    return new ConformanceResponse(req);
  }
}

class ConformanceResponse {
  JsonObject payload = null;

  ConformanceResponse(JsonObject payload) {
    this.payload = payload;
  }
}