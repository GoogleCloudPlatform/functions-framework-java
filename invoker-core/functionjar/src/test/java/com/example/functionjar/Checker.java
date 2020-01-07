package com.example.functionjar;

import com.google.escapevelocity.Template;

class Checker {
  void serviceOrAssert(String runtimeClassName) {
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
