package com.google.cloud.functions.invoker;

import com.google.auto.value.AutoValue;
import com.google.cloud.functions.Context;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import javax.annotation.Nullable;

/** Event context (metadata) for events handled by Cloud Functions. */
@AutoValue
abstract class CloudFunctionsContext implements Context {
  @Nullable
  public abstract String eventId();

  @Nullable
  public abstract String timestamp();

  @Nullable
  public abstract String eventType();

  @Nullable
  public abstract String resource();

  public static TypeAdapter<CloudFunctionsContext> typeAdapter(Gson gson) {
    return new AutoValue_CloudFunctionsContext.GsonTypeAdapter(gson);
  }

  static Builder builder() {
    return new AutoValue_CloudFunctionsContext.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setEventId(String x);
    abstract Builder setTimestamp(String x);
    abstract Builder setEventType(String x);
    abstract Builder setResource(String x);
    abstract CloudFunctionsContext build();
  }
}
