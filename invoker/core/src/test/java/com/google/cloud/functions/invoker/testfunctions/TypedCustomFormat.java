package com.google.cloud.functions.invoker.testfunctions;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.functions.TypedFunction;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TypedCustomFormat implements TypedFunction<List<String>, String> {

  @Override
  public String apply(List<String> arg) throws Exception {
    return String.join("", arg);
  }

  @Override
  public WireFormat getWireFormat() {
    return new CustomFormat();
  }
}

class CustomFormat implements TypedFunction.WireFormat {
  @Override
  public Object deserialize(HttpRequest request, Type type) throws Exception {
    List<String> req = new ArrayList<>();
    String line;
    while ((line = request.getReader().readLine()) != null) {
      req.add(line);
    }
    return req;
  }

  @Override
  public void serialize(Object object, HttpResponse response) throws Exception {
    response.getWriter().write((String) object);
  }
}
