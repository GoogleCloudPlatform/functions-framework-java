package com.google.cloud.functions.invoker;

import io.cloudevents.SpecVersion;
import io.cloudevents.core.message.impl.BaseGenericBinaryMessageReaderImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import javax.servlet.http.HttpServletRequest;

class CloudEventsServletBinaryMessageReader extends BaseGenericBinaryMessageReaderImpl<String, String> {
  private final Map<String, List<String>> headers;

  private CloudEventsServletBinaryMessageReader(Map<String, List<String>> headers, byte[] body) {
    super(SpecVersion.V1, body);
    this.headers = headers;
  }

  static CloudEventsServletBinaryMessageReader from(HttpServletRequest request, byte[] body) {
    Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (String header : Collections.list(request.getHeaderNames())) {
      for (String value : Collections.list(request.getHeaders(header))) {
        headerMap.computeIfAbsent(header, unused -> new ArrayList<>()).add(value);
      }
    }
    return new CloudEventsServletBinaryMessageReader(headerMap, body);
  }

  @Override
  protected boolean isContentTypeHeader(String header) {
    return header.equalsIgnoreCase("content-type");
  }

  @Override
  protected boolean isCloudEventsHeader(String header) {
    return header.toLowerCase(Locale.ENGLISH).startsWith("ce-");
  }

  @Override
  protected String toCloudEventsKey(String header) {
    if (!isCloudEventsHeader(header)) {
      throw new IllegalArgumentException("Not a CloudEvents header: " + header);
    }
    return header.substring(3).toLowerCase(Locale.ENGLISH);
  }

  @Override
  protected void forEachHeader(BiConsumer<String, String> consumer) {
    headers.forEach((header, values) -> values.forEach(value -> consumer.accept(header, value)));
  }

  @Override
  protected String toCloudEventsValue(String value) {
    return value;
  }

  static Map<String, List<String>> headerMap(HttpServletRequest request) {
    Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (String header : Collections.list(request.getHeaderNames())) {
      for (String value : Collections.list(request.getHeaders(header))) {
        headerMap.computeIfAbsent(header, unused -> new ArrayList<>()).add(value);
      }
    }
    return headerMap;
  }
}