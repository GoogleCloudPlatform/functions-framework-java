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

package com.example.functionjar;

import com.google.escapevelocity.Template;

class Checker {
  void serviceOrAssert(String runtimeClassName) {
    // Check that the context class loader is the loader that loaded this class.
    if (getClass().getClassLoader() != Thread.currentThread().getContextClassLoader()) {
      throw new AssertionError(
          String.format(
              "ClassLoader mismatch: mine %s; context %s",
              getClass().getClassLoader(),
              Thread.currentThread().getContextClassLoader()));
    }

    ClassLoader myLoader = getClass().getClassLoader();
    Class<Template> templateClass = Template.class;
    ClassLoader templateLoader = templateClass.getClassLoader();
    if (templateLoader != myLoader) {
      throw new AssertionError(templateLoader + " != " + myLoader);
    }
    try {
      Class<?> c = Class.forName(runtimeClassName);
      throw new AssertionError(
          "Should not be able to load " + runtimeClassName + " from " + c.getClassLoader());
    } catch (ClassNotFoundException expected) {
    }
  }
}
