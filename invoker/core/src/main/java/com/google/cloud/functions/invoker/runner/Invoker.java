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

package com.google.cloud.functions.invoker.runner;

import static java.util.stream.Collectors.toList;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.RawBackgroundFunction;
import com.google.cloud.functions.invoker.BackgroundCloudFunction;
import com.google.cloud.functions.invoker.BackgroundFunctionExecutor;
import com.google.cloud.functions.invoker.BackgroundFunctionSignatureMatcher;
import com.google.cloud.functions.invoker.FunctionLoader;
import com.google.cloud.functions.invoker.HttpCloudFunction;
import com.google.cloud.functions.invoker.HttpFunctionExecutor;
import com.google.cloud.functions.invoker.HttpFunctionSignatureMatcher;
import com.google.cloud.functions.invoker.NewBackgroundFunctionExecutor;
import com.google.cloud.functions.invoker.NewHttpFunctionExecutor;
import com.google.cloud.functions.invoker.gcf.JsonLogHandler;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Java server that runs the user's code (a jar file) on HTTP request and an HTTP response is sent
 * once the user's function is completed. The server accepts HTTP requests at '/' for executing the
 * user's function, handles all HTTP methods.
 *
 * <p>This class requires the following environment variables:
 *
 * <ul>
 *   <li>PORT - defines the port on which this server listens to HTTP requests.
 *   <li>FUNCTION_TARGET - defines the name of the method within user's class to execute.
 *   <li>FUNCTION_SIGNATURE_TYPE - determines whether the loaded code defines an HTTP or event
 *       function.
 * </ul>
 */
public class Invoker {
  private static final Logger rootLogger = Logger.getLogger("");
  private static final Logger logger = Logger.getLogger(Invoker.class.getName());

  static {
    if (isGcf()) {
      // If we're running with Google Cloud Functions, we'll get better-looking logs if we arrange
      // for them to be formatted using StackDriver's "structured logging" JSON format. Remove the
      // JDK's standard logger and replace it with the JSON one.
      for (Handler handler : rootLogger.getHandlers()) {
        rootLogger.removeHandler(handler);
      }
      rootLogger.addHandler(new JsonLogHandler(System.out, false));
    }
  }

  private static class Options {
    @Parameter(
        description = "Port on which to listen for HTTP requests.",
        names = "--port"
    )
    private String port = System.getenv().getOrDefault("PORT", "8080");

    // TODO(emcmanus): the default value here no longer makes sense and should be changed to a
    //    class name once we have finished retiring the java8 runtime.
    @Parameter(
        description = "Name of function class to execute when servicing incoming requests.",
        names = "--target"
    )
    private String target =
        System.getenv().getOrDefault("FUNCTION_TARGET", "TestFunction.function");

    @Parameter(
        description = "List of files or directories where the compiled Java classes making up"
            + " the function will be found. This functions like the -classpath option to the"
            + " java command. It is a list of filenames separated by '${path.separator}'."
            + " If an entry in the list names a directory then the class foo.bar.Baz will be looked"
            + " for in foo${file.separator}bar${file.separator}Baz.class under that"
            + " directory. If an entry in the list names a file and that file is a jar file then"
            + " class foo.bar.Baz will be looked for in an entry foo/bar/Baz.class in that jar"
            + " file. If an entry is a directory followed by '${file.separator}*' then every file"
            + " in the directory whose name ends with '.jar' will be searched for classes.",
        names = "--classpath"
    )
    private String classPath = null;

    @Parameter(
        names = "--help", help = true
    )
    private boolean help = false;
  }

  public static void main(String[] args) throws Exception {
    Optional<Invoker> invoker = makeInvoker(args);
    if (invoker.isPresent()) {
      invoker.get().startServer();
    }
  }

  static Optional<Invoker> makeInvoker(String... args) {
    return makeInvoker(System.getenv(), args);
  }

  static Optional<Invoker> makeInvoker(Map<String, String> environment, String... args) {
    Options options = new Options();
    JCommander jCommander = JCommander.newBuilder()
        .addObject(options)
        .build();
    try {
      jCommander.parse(args);
    } catch (ParameterException e) {
      usage(jCommander);
      throw e;
    }

    if (options.help) {
      usage(jCommander);
      return Optional.empty();
    }

    int port;
    try {
      port = Integer.parseInt(options.port);
    } catch (NumberFormatException e) {
      System.err.println("--port value should be an integer: " + options.port);
      usage(jCommander);
      throw e;
    }
    String functionTarget = options.target;
    Path standardFunctionJarPath = Paths.get("function/function.jar");
    Optional<String> functionClasspath =
        Arrays.asList(
            options.classPath,
            environment.get("FUNCTION_CLASSPATH"),
            Files.exists(standardFunctionJarPath) ? standardFunctionJarPath.toString() : null)
            .stream()
            .filter(Objects::nonNull)
            .findFirst();
    ClassLoader functionClassLoader = makeClassLoader(functionClasspath);
    Invoker invoker =
        new Invoker(
            port,
            functionTarget,
            environment.get("FUNCTION_SIGNATURE_TYPE"),
            functionClassLoader);
    return Optional.of(invoker);
  }

  private static void usage(JCommander jCommander) {
    StringBuilder usageBuilder = new StringBuilder();
    jCommander.getUsageFormatter().usage(usageBuilder);
    String usage = usageBuilder.toString()
        .replace("${file.separator}", File.separator)
        .replace("${path.separator}", File.pathSeparator);
    jCommander.getConsole().println(usage);
  }

  private static boolean isLocalRun() {
    return System.getenv("K_SERVICE") == null;
  }

  private static ClassLoader makeClassLoader(Optional<String> functionClasspath) {
    ClassLoader runtimeLoader = Invoker.class.getClassLoader();
    if (functionClasspath.isPresent()) {
      ClassLoader parent = new OnlyApiClassLoader(runtimeLoader);
      return new URLClassLoader(classpathToUrls(functionClasspath.get()), parent);
    }
    return runtimeLoader;
  }

  private final Integer port;
  private final String functionTarget;
  private final String functionSignatureType;
  private final ClassLoader functionClassLoader;

  public Invoker(
      Integer port,
      String functionTarget,
      String functionSignatureType,
      ClassLoader functionClassLoader) {
    this.port = port;
    this.functionTarget = functionTarget;
    this.functionSignatureType = functionSignatureType;
    this.functionClassLoader = functionClassLoader;
  }

  Integer getPort() {
    return port;
  }

  String getFunctionTarget() {
    return functionTarget;
  }

  String getFunctionSignatureType() {
    return functionSignatureType;
  }

  ClassLoader getFunctionClassLoader() {
    return functionClassLoader;
  }

  public void startServer() throws Exception {
    Server server = new Server(port);

    ServletContextHandler servletContextHandler = new ServletContextHandler();
    servletContextHandler.setContextPath("/");
    server.setHandler(NotFoundHandler.forServlet(servletContextHandler));

    Optional<Class<?>> functionClass = loadFunctionClass();

    HttpServlet servlet;
    if ("http".equals(functionSignatureType)) {
      if (functionClass.isPresent()) {
        servlet = NewHttpFunctionExecutor.forClass(functionClass.get());
      } else {
        FunctionLoader<HttpCloudFunction> loader = new FunctionLoader<>(
            functionTarget, functionClassLoader, new HttpFunctionSignatureMatcher());
        HttpCloudFunction function = loader.loadUserFunction();
        servlet = new HttpFunctionExecutor(function);
      }
    } else if ("event".equals(functionSignatureType)) {
      if (functionClass.isPresent()) {
        servlet = NewBackgroundFunctionExecutor.forClass(functionClass.get());
      } else {
        FunctionLoader<BackgroundCloudFunction> loader =
            new FunctionLoader<>(
                functionTarget, functionClassLoader, new BackgroundFunctionSignatureMatcher());
        BackgroundCloudFunction function = loader.loadUserFunction();
        servlet = new BackgroundFunctionExecutor(function);
      }
    } else if (functionSignatureType == null) {
      if (functionClass.isPresent()) {
        servlet = servletForDeducedSignatureType(functionClass.get());
      } else {
        throw new RuntimeException(
            "Class " + functionTarget + " does not exist or could not be loaded");
      }
    } else {
      String error = String.format(
          "Function signature type %s is unknown; should be \"http\" or \"event\"",
          functionSignatureType);
      throw new RuntimeException(error);
    }
    servletContextHandler.addServlet(new ServletHolder(servlet), "/*");

    server.start();
    logServerInfo();
    server.join();
  }

  private Optional<Class<?>> loadFunctionClass() {
    String target = functionTarget;
    while (true) {
      try {
        return Optional.of(functionClassLoader.loadClass(target));
      } catch (ClassNotFoundException e) {
        // This might be a nested class like com.example.Foo.Bar. That will actually appear as
        // com.example.Foo$Bar as far as Class.forName is concerned. So we try to replace every dot
        // from the last to the first with a $ in the hope of finding a class we can load.
        int lastDot = target.lastIndexOf('.');
        if (lastDot < 0) {
          return Optional.empty();
        }
        target = target.substring(0, lastDot) + '$' + target.substring(lastDot + 1);
      }
    }
  }

  private HttpServlet servletForDeducedSignatureType(Class<?> functionClass) {
    if (HttpFunction.class.isAssignableFrom(functionClass)) {
      return NewHttpFunctionExecutor.forClass(functionClass);
    }
    if (BackgroundFunction.class.isAssignableFrom(functionClass)
        || RawBackgroundFunction.class.isAssignableFrom(functionClass)) {
      return NewBackgroundFunctionExecutor.forClass(functionClass);
    }
    String error = String.format(
        "Could not determine function signature type from target %s. Either this should be"
            + " a class implementing one of the interfaces in com.google.cloud.functions, or the"
            + " environment variable FUNCTION_SIGNATURE_TYPE should be set to \"http\" or"
            + " \"event\".",
        functionTarget);
    throw new RuntimeException(error);
  }

  static URL[] classpathToUrls(String classpath) {
    String[] components = classpath.split(File.pathSeparator);
    List<URL> urls = new ArrayList<>();
    for (String component : components) {
      if (component.endsWith(File.separator + "*")) {
        urls.addAll(jarsIn(component.substring(0, component.length() - 2)));
      } else {
        Path path = Paths.get(component);
        try {
          urls.add(path.toUri().toURL());
        } catch (MalformedURLException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
    return urls.toArray(new URL[0]);
  }

  private static List<URL> jarsIn(String dir) {
    Path path = Paths.get(dir);
    if (!Files.isDirectory(path)) {
      return Collections.emptyList();
    }
    Stream<Path> stream;
    try {
      stream = Files.list(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return stream
        .filter(p -> p.getFileName().toString().endsWith(".jar"))
        .map(p -> {
          try {
            return p.toUri().toURL();
          } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
          }
        })
        .collect(toList());
  }

  private void logServerInfo() {
    if (!isGcf()) {
      logger.log(Level.INFO, "Serving function...");
      logger.log(Level.INFO, "Function: {0}", functionTarget);
      logger.log(Level.INFO, "URL: http://localhost:{0,number,#}/", port);
    }
  }

  private static boolean isGcf() {
    // This environment variable is set in the GCF environment but won't be set when invoking
    // the Functions Framework directly. We don't use its value, just whether it is set.
    return System.getenv("K_SERVICE") != null;
  }

  /**
   * Wrapper that intercepts requests for {@code /favicon.ico} and {@code /robots.txt} and causes
   * them to produce a 404 status. Otherwise they would be sent to the function code, like any
   * other URL, meaning that someone testing their function by using a browser as an HTTP client
   * can see two requests, one for {@code /favicon.ico} and one for {@code /} (or whatever).
   */
  private static class NotFoundHandler extends HandlerWrapper {
    static NotFoundHandler forServlet(ServletContextHandler servletHandler) {
      NotFoundHandler handler = new NotFoundHandler();
      handler.setHandler(servletHandler);
      return handler;
    }

    private static final Set<String> NOT_FOUND_PATHS =
        new HashSet<>(Arrays.asList("/favicon.ico", "/robots.txt"));

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
      if (NOT_FOUND_PATHS.contains(request.getRequestURI())) {
        response.sendError(HttpStatus.NOT_FOUND_404, "Not Found");
      }
      super.handle(target, baseRequest, request, response);
    }
  }

  /**
   * A loader that only loads GCF API classes. Those are classes whose package is exactly
   * {@code com.google.cloud.functions}. The package can't be a subpackage, such as
   * {@code com.google.cloud.functions.invoker}.
   *
   * <p>This loader allows us to load the classes from a user function, without making the
   * runtime classes visible to them.  We will make this loader the parent of the
   * {@link URLClassLoader} that loads the user code in order to filter out those runtime classes.
   *
   * <p>The reason we do need to share the API classes between the runtime and the user function is
   * so that the runtime can instantiate the function class and cast it to
   * {@link com.google.cloud.functions.HttpFunction} or whatever.
   */
  private static class OnlyApiClassLoader extends ClassLoader {
    private final ClassLoader runtimeClassLoader;

    OnlyApiClassLoader(ClassLoader runtimeClassLoader) {
      super(getSystemOrBootstrapClassLoader());
      this.runtimeClassLoader = runtimeClassLoader;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      String prefix = "com.google.cloud.functions.";
      if ((name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length())))
              || name.startsWith("javax.servlet.")) {
        return runtimeClassLoader.loadClass(name);
      }
      return super.findClass(name); // should throw ClassNotFoundException
    }

    private static ClassLoader getSystemOrBootstrapClassLoader() {
      try {
        // We're still building against the Java 8 API, so we have to use reflection for now.
        Method getPlatformClassLoader = ClassLoader.class.getMethod("getPlatformClassLoader");
        return (ClassLoader) getPlatformClassLoader.invoke(null);
      } catch (ReflectiveOperationException e) {
        return null;
      }
    }
  }
}
