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
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Map;

/** Event context (metadata) for events handled by Cloud Functions. */
@AutoValue
abstract class CloudFunctionsContext implements Context {
  // AutoValue recognizes any annotation called @Nullable, so no need to import this from anywhere.
  @Retention(RetentionPolicy.SOURCE)
  @interface Nullable {}

  @Override
  @Nullable
  public abstract String eventId();

  @Override
  @Nullable
  public abstract String timestamp();

  @Override
  @Nullable
  public abstract String eventType();

  @Override
  @Nullable
  public abstract String resource();

  // TODO: expose this in the Context interface (as a default method).
  abstract Map<String, String> params();

  @Nullable
  abstract String domain();

  @Override
  public abstract Map<String, String> attributes();

  public static TypeAdapter<CloudFunctionsContext> typeAdapter(Gson gson) {
    return new AutoValue_CloudFunctionsContext.GsonTypeAdapter(gson);
  }

  static Builder builder() {
    return new AutoValue_CloudFunctionsContext.Builder()
        .setParams(Collections.emptyMap())
        .setAttributes(Collections.emptyMap());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setEventId(String x);
    abstract Builder setTimestamp(String x);
    abstract Builder setEventType(String x);
    abstract Builder setResource(String x);
    abstract Builder setParams(Map<String, String> x);
    abstract Builder setAttributes(Map<String, String> value);
    abstract Builder setDomain(String x);

    abstract CloudFunctionsContext build();
  }

  /**
   * Depending on the event type, the {@link Context#resource()} field is either a JSON string (complete
   * with encosing quotes) or a JSON object. This class allows us to redeserialize that JSON representation
   * into its components.
   */
  @AutoValue
  abstract static class Resource {
    abstract @Nullable String service();
    abstract String name();
    abstract @Nullable String type();

    static TypeAdapter<Resource> typeAdapter(Gson gson) {
      return new AutoValue_CloudFunctionsContext_Resource.GsonTypeAdapter(gson);
    }

    static Resource from(String s) {
      Gson baseGson = new Gson();
      if (s.startsWith("\"") && s.endsWith("\"")) {
        String name = baseGson.fromJson(s, String.class);
        return builder().setName(name).build();
      }
      if (s.startsWith("{") && (s.endsWith("}") || s.endsWith("}\n"))) {
        TypeAdapter<Resource> typeAdapter = typeAdapter(baseGson);
        Gson gson = new GsonBuilder().registerTypeAdapter(Resource.class, typeAdapter).create();
        return gson.fromJson(s, Resource.class);
      }
      throw new IllegalArgumentException("Unexpected resource syntax: " + s);
    }

    static Builder builder() {
      return new AutoValue_CloudFunctionsContext_Resource.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setService(String x);
      abstract Builder setName(String x);
      abstract Builder setType(String x);
      abstract Resource build();
    }
  }
}
