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
