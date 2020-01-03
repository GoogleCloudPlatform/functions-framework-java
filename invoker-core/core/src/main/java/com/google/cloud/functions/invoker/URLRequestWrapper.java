package com.google.cloud.functions.invoker;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/** Removes the servlet path from the request URL seen by the client. */
class URLRequestWrapper extends HttpServletRequestWrapper {
  private final String newValue;

  URLRequestWrapper(HttpServletRequest req) {
    super(req);
    if (req.getRequestURL() != null && req.getServletPath() != null) {
      this.newValue = req.getRequestURL().toString().replaceFirst(req.getServletPath(), "");
    } else {
      this.newValue = null;
    }
  }

  @Override
  public StringBuffer getRequestURL() {
    if (newValue == null) {
      return super.getRequestURL();
    }
    return new StringBuffer(newValue);
  }
}
