// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.functions.invoker;

import com.google.auto.value.AutoValue;
import com.google.cloud.functions.Context;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Event context (metadata) for events handled by Cloud Functions. */
@AutoValue
abstract class CloudFunctionsContext implements Context {
  // AutoValue recognizes any annotation called @Nullable, so no need to import this from anywhere.
  @Retention(RetentionPolicy.SOURCE)
  @interface Nullable {}

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
