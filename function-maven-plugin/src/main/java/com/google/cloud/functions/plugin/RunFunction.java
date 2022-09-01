package com.google.cloud.functions.plugin;

import com.google.cloud.functions.invoker.runner.Invoker;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Runs a function using the Java Functions Framework. Typically this plugin is configured in one of
 * two ways. Either in the pom.xml file, like this...
 *
 * <pre>{@code
 * <plugin>
 *   <groupId>com.google.cloud.functions</groupId>
 *   <artifactId>function-maven-plugin</artifactId>
 *   <version>1.0.0-alpha-2-rc3</version>
 *   <configuration>
 *     <functionTarget>com.example.function.Echo</functionTarget>
 *   </configuration>
 * </plugin>
 * }</pre>
 *
 * ...and then run using {@code mvn function:run}. Or using properties on the command line, like
 * this...<br>
 *
 * <pre>{@code
 * mvn com.google.cloud.functions:function:1.0.0-alpha-2-rc3:run \
 *     -Drun.functionTarget=com.example.function.Echo
 * }</pre>
 */
@Mojo(
    name = "run",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    requiresDependencyCollection = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
public class RunFunction extends AbstractMojo {

  /**
   * The name of the function to run. This is the name of a class that implements one of the
   * interfaces in {@code com.google.cloud.functions}.
   */
  @Parameter(property = "run.functionTarget")
  private String functionTarget;

  /** The port on which the HTTP server wrapping the function should listen. */
  @Parameter(property = "run.port", defaultValue = "8080")
  private Integer port;

  /**
   * Used to determine what classpath needs to be used to load the function. This parameter is
   * injected by Maven and can't be set explicitly in a pom.xml file.
   */
  @Parameter(defaultValue = "${project.runtimeClasspathElements}", readonly = true, required = true)
  private List<String> runtimePath;

  public void execute() throws MojoExecutionException {
    String classpath = String.join(File.pathSeparator, runtimePath);
    List<String> args = new ArrayList<>();
    args.addAll(Arrays.asList("--classpath", classpath));
    if (functionTarget != null) {
      args.addAll(Arrays.asList("--target", functionTarget));
    }
    if (port != null) {
      args.addAll(Arrays.asList("--port", String.valueOf(port)));
    }
    try {
      getLog().info("Calling Invoker with " + args);
      Invoker.main(args.toArray(new String[0]));
    } catch (Exception e) {
      getLog().error("Could not invoke function: " + e, e);
      throw new MojoExecutionException("Could not invoke function", e);
    }
  }
}
