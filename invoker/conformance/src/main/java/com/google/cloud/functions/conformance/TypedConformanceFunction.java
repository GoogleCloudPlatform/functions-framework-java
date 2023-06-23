package com.google.cloud.functions.conformance;

import com.google.cloud.functions.TypedFunction;
import com.google.gson.annotations.SerializedName;

public class TypedConformanceFunction implements TypedFunction<ConformanceRequest, ConformanceResponse> {
  @Override
  public ConformanceResponse apply(ConformanceRequest req) throws Exception {
    return new ConformanceResponse(req);
  }
}

class ConformanceRequest {
  @SerializedName("message")
  public String message;
}

class ConformanceResponse {
  @SerializedName("payload")
  public ConformanceRequest payload = null;

  ConformanceResponse(ConformanceRequest payload) {
    this.payload = payload;
  }
}