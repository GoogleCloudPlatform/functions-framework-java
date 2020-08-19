package com.google.cloud.functions;

import io.cloudevents.CloudEvent;

/**
 * Represents a Cloud Function that is activated by an event and parsed into a {@link CloudEvent} object.
 * Because the {@link CloudEvent} API is not yet stable, a function implemented using this class may not
 * build or work correctly with later versions of that API. Once the API is stable, this interface will
 * become {@code CloudEventsFunction} and will also be stable.
 */
@FunctionalInterface
public interface ExperimentalCloudEventsFunction {
  /**
   * Called to service an incoming event. This interface is implemented by user code to
   * provide the action for a given background function. If this method throws any exception
   * (including any {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param event the event.
   * @throws Exception to produce a 500 status code in the HTTP response.
   */
  void accept(CloudEvent event) throws Exception;
}
