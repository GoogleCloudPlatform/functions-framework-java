package com.google.cloud.functions.invoker.runner;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.cloud.functions.invoker.BackgroundCloudFunction;
import com.google.cloud.functions.invoker.BackgroundFunctionExecutor;
import com.google.cloud.functions.invoker.BackgroundFunctionSignatureMatcher;
import com.google.cloud.functions.invoker.FunctionLoader;
import com.google.cloud.functions.invoker.HttpCloudFunction;
import com.google.cloud.functions.invoker.HttpFunctionExecutor;
import com.google.cloud.functions.invoker.HttpFunctionSignatureMatcher;
import com.google.cloud.functions.invoker.NewBackgroundFunctionExecutor;
import com.google.cloud.functions.invoker.NewHttpFunctionExecutor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import org.eclipse.jetty.server.Server;
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

  private static final Logger logger;

  static {
    try {
      LogManager.getLogManager()
          .readConfiguration(Invoker.class.getResourceAsStream("/logging.properties"));
    } catch (IOException e) {
      System.out.println("Failed to read logging configuration file, error: " + e.getMessage());
    }
    logger = Logger.getLogger(Invoker.class.getName());
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
        description = "Name of a jar file that contains the function to execute. This must be"
            + " self-contained: either it must be a \"fat jar\" which bundles the dependencies"
            + " of all of the function code, or it must use the Class-Path attribute in the jar"
            + " manifest to point to those dependencies.",
        names = "--jar"
    )
    private String jar = null;

    @Parameter(
        names = "--help", help = true
    )
    private boolean help = false;
  }

  public static void main(String[] args) throws Exception {
    Options options = new Options();
    JCommander jCommander = JCommander.newBuilder()
        .addObject(options)
        .build();
    try {
      jCommander.parse(args);
    } catch (ParameterException e) {
      jCommander.usage();
      throw e;
    }

    if (options.help) {
      jCommander.usage();
      return;
    }

    int port;
    try {
      port = Integer.parseInt(options.port);
    } catch (NumberFormatException e) {
      System.err.println("--port value should be an integer: " + options.port);
      jCommander.usage();
      throw e;
    }
    String functionTarget = options.target;
    Path standardFunctionJarPath = Paths.get("function/function.jar");
    Optional<String> functionJarPath =
        Arrays.asList(
            options.jar,
            System.getenv("FUNCTION_JAR"),
            Files.exists(standardFunctionJarPath) ? standardFunctionJarPath.toString() : null)
            .stream()
            .filter(Objects::nonNull)
            .findFirst();
    Invoker invoker =
        new Invoker(
            port,
            functionTarget,
            // TODO: remove once function signature type is inferred from the method signature.
            System.getenv("FUNCTION_SIGNATURE_TYPE"),
            functionJarPath);
    invoker.startServer();
  }

  private static boolean isLocalRun() {
    return System.getenv("K_SERVICE") == null;
  }

  private final Integer port;
  private final String functionTarget;
  private final String functionSignatureType;
  private final Optional<String> functionJarPath;

  public Invoker(
      Integer port,
      String functionTarget,
      String functionSignatureType,
      Optional<String> functionJarPath) {
    this.port = port;
    this.functionTarget = functionTarget;
    this.functionSignatureType = functionSignatureType;
    this.functionJarPath = functionJarPath;
  }

  public void startServer() throws Exception {
    Server server = new Server(port);

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    server.setHandler(context);

    Optional<File> functionJarFile =
        functionJarPath.isPresent()
            ? Optional.of(new File(functionJarPath.get()))
            : Optional.empty();
    if (functionJarFile.isPresent() && !functionJarFile.get().exists()) {
      throw new IllegalArgumentException(
          "functionJarPath points to an non-existent file: "
              + functionJarFile.get().getAbsolutePath());
    }

    ClassLoader runtimeLoader = getClass().getClassLoader();
    ClassLoader classLoader;
    if (functionJarFile.isPresent()) {
      ClassLoader parent = new OnlyApiClassLoader(runtimeLoader);
      classLoader = new URLClassLoader(new URL[]{functionJarFile.get().toURI().toURL()}, parent);
    } else {
      classLoader = runtimeLoader;
    }

    HttpServlet servlet;
    if ("http".equals(functionSignatureType)) {
      Optional<NewHttpFunctionExecutor> newExecutor =
          NewHttpFunctionExecutor.forTarget(functionTarget, classLoader);
      if (newExecutor.isPresent()) {
        servlet = newExecutor.get();
      } else {
        FunctionLoader<HttpCloudFunction> loader =
            new FunctionLoader<>(functionTarget, classLoader, new HttpFunctionSignatureMatcher());
        HttpCloudFunction function = loader.loadUserFunction();
        servlet = new HttpFunctionExecutor(function);
      }
    } else if ("event".equals(functionSignatureType)) {
      Optional<NewBackgroundFunctionExecutor> newExecutor =
          NewBackgroundFunctionExecutor.forTarget(functionTarget, classLoader);
      if (newExecutor.isPresent()) {
        servlet = newExecutor.get();
      } else {
        FunctionLoader<BackgroundCloudFunction> loader =
            new FunctionLoader<>(
                functionTarget, classLoader, new BackgroundFunctionSignatureMatcher());
        BackgroundCloudFunction function = loader.loadUserFunction();
        servlet = new BackgroundFunctionExecutor(function);
      }
    } else if (functionSignatureType == null) {
      Optional<NewHttpFunctionExecutor> httpExecutor =
          NewHttpFunctionExecutor.forTarget(functionTarget, classLoader);
      if (httpExecutor.isPresent()) {
        servlet = httpExecutor.get();
      } else {
        Optional<NewBackgroundFunctionExecutor> backgroundExecutor =
            NewBackgroundFunctionExecutor.forTarget(functionTarget, classLoader);
        if (backgroundExecutor.isPresent()) {
          servlet = backgroundExecutor.get();
        } else {
          String error = String.format(
              "Could not determine function signature type from target %s. Either this should be"
              + " a class implementing one of the interfaces in com.google.cloud.functions, or the"
              + " environment variable FUNCTION_SIGNATURE_TYPE should be set to \"http\" or"
              + " \"event\".",
              functionTarget);
          throw new RuntimeException(error);
        }
      }
    } else {
      String error = String.format(
          "Function signature type %s is unknown; should be \"http\" or \"event\"",
          functionSignatureType);
      throw new RuntimeException(error);
    }
    context.addServlet(new ServletHolder(servlet), "/*");

    server.start();
    logServerInfo();
    server.join();
  }

  private void logServerInfo() {
    if (isLocalRun()) {
      logger.log(Level.INFO, "Serving function...");
      logger.log(Level.INFO, "Function: {0}", functionTarget);
      logger.log(Level.INFO, "URL: http://localhost:{0,number,#}/", port);
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
