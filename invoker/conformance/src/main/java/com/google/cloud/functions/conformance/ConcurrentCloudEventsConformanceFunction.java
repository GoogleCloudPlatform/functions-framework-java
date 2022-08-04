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

import com.google.cloud.functions.CloudEventsFunction;
import io.cloudevents.CloudEvent;

/**
 * This class is used by the Functions Framework Conformance Tools to validate the framework's Cloud
 * Events API. It can be run with the following command:
 *
 * <pre>{@code
 * $ functions-framework-conformance-client \
 *   -cmd="mvn function:run -Drun.functionTarget=com.google.cloud.functions.conformance.ConcurrentCloudEventsConformanceFunction" \
 *   -type=cloudevent \
 *   -buildpacks=false \
 *   -validate-mapping=false \
 *   -start-delay=5 \
 *   -validate-concurrency=true
 * }</pre>
 */
public class ConcurrentCloudEventsConformanceFunction implements CloudEventsFunction {

  @Override
  public void accept(CloudEvent event) throws Exception {
    Thread.sleep(1000);
  }
}
