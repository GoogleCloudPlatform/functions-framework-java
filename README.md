# Functions Framework for Java

[![Maven Central (functions-framework-api)](https://img.shields.io/maven-central/v/com.google.cloud.functions/functions-framework-api.svg?label=functions-framework-api)](https://search.maven.org/artifact/com.google.cloud.functions/functions-framework-api)
[![Maven Central (java-function-invoker)](https://img.shields.io/maven-central/v/com.google.cloud.functions.invoker/java-function-invoker.svg?label=java-function-invoker)](https://search.maven.org/artifact/com.google.cloud.functions.invoker/java-function-invoker)
[![Maven Central (function-maven-plugin)](https://img.shields.io/maven-central/v/com.google.cloud.functions/function-maven-plugin.svg?label=function-maven-plugin)](https://search.maven.org/artifact/com.google.cloud.functions/function-maven-plugin)

[![Java Unit CI](https://github.com/GoogleCloudPlatform/functions-framework-java/actions/workflows/unit.yaml/badge.svg)](https://github.com/GoogleCloudPlatform/functions-framework-java/actions/workflows/unit.yaml)
[![Java Lint CI](https://github.com/GoogleCloudPlatform/functions-framework-java/actions/workflows/lint.yaml/badge.svg)](https://github.com/GoogleCloudPlatform/functions-framework-java/actions/workflows/lint.yaml)
[![Java Conformance CI](https://github.com/GoogleCloudPlatform/functions-framework-java/actions/workflows/conformance.yaml/badge.svg)](https://github.com/GoogleCloudPlatform/functions-framework-java/actions/workflows/conformance.yaml)

An open source FaaS (Function as a service) framework for writing portable
Java functions -- brought to you by the Google Cloud Functions team.

The Functions Framework lets you write lightweight functions that run in many
different environments, including:

*   [Google Cloud Functions](https://cloud.google.com/functions/)
*   Your local development machine
*   [Cloud Run](https://cloud.google.com/run/) and [Cloud Run for Anthos](https://cloud.google.com/anthos/run/)
*   [Knative](https://github.com/knative/)-based environments

## Installation

The Functions Framework for Java uses
[Java](https://java.com/en/download/help/download_options.xml) and
[Maven](http://maven.apache.org/install.html) (the `mvn` command),
for building and deploying functions from source.

However, it is also possible to build your functions using
[Gradle](https://gradle.org/), as JAR archives, that you will deploy with the 
`gcloud` command-line.

## Quickstart: Hello, World on your local machine

A function is typically structured as a Maven project. We recommend using an IDE
that supports Maven to create the Maven project. Add this dependency in the
`pom.xml` file of your project:

```xml
    <dependency>
      <groupId>com.google.cloud.functions</groupId>
      <artifactId>functions-framework-api</artifactId>
      <version>1.0.4</version>
      <scope>provided</scope>
    </dependency>
```

If you are using Gradle to build your functions, you can define the Functions
Framework dependency in your `build.gradle` project file as follows:

```groovy
    dependencies {
        implementation 'com.google.cloud.functions:functions-framework-api:1.0.4'
    }

```

### Writing an HTTP function

Create a file `src/main/java/com/example/HelloWorld.java` with the following
contents:

```java
package com.example;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class HelloWorld implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response)
      throws Exception {
    response.getWriter().write("Hello, World\n");
  }
}
```


## Quickstart: Create a Background Function

There are two ways to write a Background function, which differ in how the
payload of the incoming event is represented. In a "raw" background function
this payload is presented as a JSON-encoded Java string. In a "typed" background
function the Functions Framework deserializes the JSON payload into a Plain Old
Java Object (POJO).

### Writing a Raw Background Function

Create a file `src/main/java/com/example/Background.java` with the following
contents:

```java
package com.example;

import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.logging.Logger;

public class Background implements RawBackgroundFunction {
  private static final Logger logger =
      Logger.getLogger(Background.class.getName());

  @Override
  public void accept(String json, Context context) {
    Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
    logger.info("Received JSON object: " + jsonObject);
  }
}
```

### Writing a Typed Background Function

Create a file `src/main/java/com/example/PubSubBackground` with the following
contents:

```java
package com.example;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import java.util.Map;
import java.util.logging.Logger;

// This is the Pub/Sub message format from the Pub/Sub emulator. 
class PubSubMessage {
  String data;
  Map<String, String> attributes;
  String messageId;
  String publishTime;
}

public class PubSubBackground implements BackgroundFunction<PubSubMessage> {
  private static final Logger logger =
      Logger.getLogger(PubSubBackground.class.getName());

  @Override
  public void accept(PubSubMessage pubSubMessage, Context context) {
    logger.info("Received message with id " + context.eventId());
  }
}
```


## Running a function with the Maven plugin

The Maven plugin called `function-maven-plugin` allows you to run functions
on your development machine.

### Configuration in `pom.xml`

You can configure the plugin in `pom.xml`:

```xml
<plugin>
  <groupId>com.google.cloud.functions</groupId>
  <artifactId>function-maven-plugin</artifactId>
  <version>0.9.7</version>
  <configuration>
    <functionTarget>com.example.function.Echo</functionTarget>
  </configuration>
</plugin>
```

Then run it from the command line:

```sh
mvn function:run
```

### Configuration on the command line

You can alternatively configure the plugin with properties on the command line:

```sh
  mvn com.google.cloud.functions:function-maven-plugin:0.9.7:run \
      -Drun.functionTarget=com.example.function.Echo
```

### Running the Functions Framework directly

You can also run a function by using the Functions Framework jar directly.
Copy the Functions Framework jar to a local location like this:

```sh
mvn dependency:copy \
    -Dartifact='com.google.cloud.functions.invoker:java-function-invoker:1.0.2' \
    -DoutputDirectory=.
```

In this example we use the current directory `.` but you can specify any other
directory to copy to. Then run your function:

```sh
java -jar java-function-invoker-1.0.2 \
    --classpath myfunction.jar \
    --target com.example.HelloWorld
```


## Running a function with Gradle

From Gradle, similarily to running functions with the Functions Framework jar,
we can invoke the `Invoker` class with a `JavaExec` task.

### Configuration in `build.gradle`

```groovy
configurations {
    invoker
}

dependencies {
    implementation 'com.google.cloud.functions:functions-framework-api:1.0.4'
    invoker 'com.google.cloud.functions.invoker:java-function-invoker:1.0.2'
}

tasks.register("runFunction", JavaExec) {
    main = 'com.google.cloud.functions.invoker.runner.Invoker'
    classpath(configurations.invoker)
    inputs.files(configurations.runtimeClasspath, sourceSets.main.output)
    args(
            '--target', project.findProperty('run.functionTarget'),
            '--port', project.findProperty('run.port') ?: 8080
    )
    doFirst {
        args('--classpath', files(configurations.runtimeClasspath, sourceSets.main.output).asPath)
    }
}
```

Then in your terminal or IDE, you will be able to run the function locally with:

```sh
gradle runFunction -Prun.functionTarget=com.example.HelloWorld \
                   -Prun.port=8080
```

Or if you use the Gradle wrapper provided by your Gradle project build:

```sh
./gradlew runFunction -Prun.functionTarget=com.example.HelloWorld \
                      -Prun.port=8080
```

## Functions Framework configuration

There are a number of options that can be used to configure the Functions
Framework, whether run directly or on the command line.

### Which function to run

A function is a Java class. You must specify the name of that class when running
the Functions Framework:

```
--target com.example.HelloWorld
<functionTarget>com.example.HelloWorld</functionTarget>
-Drun.functionTarget=com.example.HelloWorld
-Prun.functionTarget=com.example.HelloWorld
```

* Invoker argument: `--target com.example.HelloWorld`
* Maven `pom.xml`: `<functionTarget>com.example.HelloWorld</functionTarget>`
* Maven CLI argument: `-Drun.functionTarget=com.example.HelloWorld`
* Gradle CLI argument: `-Prun.functionTarget=com.example.HelloWorld`

### Which port to listen on

The Functions Framework is an HTTP server that directs incoming HTTP requests to
the function code. By default this server listens on port 8080. Specify an
alternative value like this:

* Invoker argument: `--port 12345`
* Maven `pom.xml`: `<port>12345</port>`
* Maven CLI argument: `-Drun.port=12345`
* Gradle CLI argument: `-Prun.port=12345`

### Function classpath

Function code runs with a classpath that includes the function code itself and
its dependencies. The Maven plugin automatically computes the classpath based
on the dependencies expressed in `pom.xml`. When invoking the Functions
Framework directly, you must use `--classpath` to indicate how to find the code
and its dependencies. For example:

```
java -jar java-function-invoker-1.0.2 \
    --classpath 'myfunction.jar:/some/directory:/some/library/*' \
    --target com.example.HelloWorld
```

The `--classpath` option works like
[`java -classpath`](https://docs.oracle.com/en/java/javase/13/docs/specs/man/java.html#standard-options-for-java).
It is a list of entries separated by `:` (`;` on Windows), where each entry is:

* a directory, in which case class `com.example.Foo` is looked for in a file
  `com/example/Foo.class` under that directory;
* a jar file, in which case class `com.example.Foo` is looked for in a file
  `com/example/Foo.class` in that jar file;
* a directory followed by `/*` (`\*` on Windows), in which case each jar file
  in that directory (file called `foo.jar`) is treated the same way as if it
  had been named explicitly.

#### Simplifying the claspath

Specifying the right classpath can be tricky. A simpler alternative is to
build the function as a "fat jar", where the function code and all its
dependencies are in a single jar file. Then `--classpath myfatfunction.jar`
is enough. An example of how this is done is the Functions Framework jar itself,
as seen
[here](https://github.com/GoogleCloudPlatform/functions-framework-java/blob/b627f28/invoker/core/pom.xml#L153).

Alternatively, you can arrange for your jar to have its own classpath, as
described
[here](https://maven.apache.org/shared/maven-archiver/examples/classpath.html).
