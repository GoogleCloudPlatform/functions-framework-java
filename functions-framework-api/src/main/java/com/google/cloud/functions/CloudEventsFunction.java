package com.google.cloud.functions;

import io.cloudevents.CloudEvent;

/**
 * Represents a Cloud Function that is activated by an event and parsed into a {@link CloudEvent}
 * object.
 */
@FunctionalInterface
public interface CloudEventsFunction {
  /**
   * Called to service an incoming event. This interface is implemented by user code to provide the
   * action for a given background function. If this method throws any exception (including any
   * {@link Error}) then the HTTP response will have a 500 status code.
   *
   * @param event the event.
   * @throws Exception to produce a 500 status code in the HTTP response.
   */
  void accept(CloudEvent event) throws Exception;
}
