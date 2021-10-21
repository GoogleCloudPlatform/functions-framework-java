// Copyright 2019 Google LLC
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

package com.google.cloud.functions;

import java.util.Collections;
import java.util.Map;

/** An interface for event function context. */
public interface Context {
  /**
   * Returns event ID.
   *
   * @return event ID
   */
  String eventId();

  /**
   * Returns event timestamp.
   *
   * @return event timestamp
   */
  String timestamp();

  /**
   * Returns event type.
   *
   * @return event type
   */
  String eventType();

  /**
   * Returns event resource.
   *
   * @return event resource
   */
  String resource();

  /**
   * Returns additional attributes from this event. For CloudEvents, the entries in this map will
   * include the <a
   * href="https://github.com/cloudevents/spec/blob/v1.0/spec.md#required-attributes">required
   * attributes</a> and may include <a
   * href="https://github.com/cloudevents/spec/blob/v1.0/spec.md#required-attributes">optional
   * attributes</a> and <a
   * href="https://github.com/cloudevents/spec/blob/v1.0/spec.md#extension-context-attributes">
   * extension attributes</a>.
   *
   * <p>The map returned by this method may be empty but is never null.
   *
   * @return additional attributes form this event.
   */
  default Map<String, String> attributes() {
    return Collections.emptyMap();
  }
}
