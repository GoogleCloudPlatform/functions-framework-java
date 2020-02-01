package com.google.cloud.functions.invoker.runner;

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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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

  public static void main(String[] args) throws Exception {

    CommandLine line = parseCommandLineOptions(args);

    int port =
        Arrays.asList(line.getOptionValue("port"), System.getenv("PORT")).stream()
            .filter(Objects::nonNull)
            .findFirst()
            .map(Integer::parseInt)
            .orElse(8080);
    String functionTarget =
        Arrays.asList(line.getOptionValue("target"), System.getenv("FUNCTION_TARGET")).stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("TestFunction.function");
    Path standardFunctionJarPath = Paths.get("function/function.jar");
    Optional<String> functionJarPath =
        Arrays.asList(
                line.getOptionValue("jar"),
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

  private static CommandLine parseCommandLineOptions(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption("port", true, "the port on which server listens to HTTP requests");
    options.addOption("target", true, "fully qualified name of the target method to execute");
    options.addOption("jar", true, "path to function jar");

    try {
      CommandLine line = parser.parse(options, args);
      return line;
    } catch (ParseException e) {
      logger.log(Level.SEVERE, "Failed to parse command line options", e);
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("Invoker", options);
      System.exit(1);
    }
    return null;
  }

  private final Integer port;
  private final String functionTarget;
  private final String functionSignatureType;
  private final Optional<String> functionJarPath;

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
