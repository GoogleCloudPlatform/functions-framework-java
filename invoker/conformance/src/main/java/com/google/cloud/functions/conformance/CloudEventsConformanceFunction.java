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

package com.google.cloud.functions.conformance;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.functions.CloudEventsFunction;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * This class is used by the Functions Framework Conformance Tools to validate the framework's Cloud
 * Events API. It can be run with the following command:
 *
 * <pre>{@code
 * $ functions-framework-conformance-client \
 *   -cmd="mvn function:run -Drun.functionTarget=com.google.cloud.functions.conformance.CloudEventsConformanceFunction" \
 *   -type=cloudevent \
 *   -buildpacks=false \
 *   -validate-mapping=false \
 *   -start-delay=5
 * }</pre>
 */
public class CloudEventsConformanceFunction implements CloudEventsFunction {

  @Override
  public void accept(CloudEvent event) throws Exception {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter("function_output.json"))) {
      EventFormat format = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
      writer.write(new String(format.serialize(event), UTF_8));
    }
  }
}
