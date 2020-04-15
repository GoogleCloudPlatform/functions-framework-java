package com.google.cloud.functions.plugin;

import com.google.cloud.functions.invoker.runner.Invoker;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.*;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import javax.inject.Inject;

/**
 * Runs a function using the Java Functions Framework. Typically this plugin is configured in one
 * of two ways. Either in the pom.xml file, like this...
 *
 * <pre>{@code
 *  <plugin>
 *    <groupId>com.google.cloud.functions</groupId>
 *    <artifactId>function-maven-plugin</artifactId>
 *    <version>1.0.0-alpha-2-rc3</version>
 *    <configuration>
 *      <functionTarget>com.example.function.Echo</functionTarget>
 *    </configuration>
 *  </plugin>
 * }</pre>
 *
 * ...and then run using {@code mvn function:run}. Or using properties on the command line, like
 * this...<br>
 *
 * <pre>{@code
 *   mvn com.google.cloud.functions:function:1.0.0-alpha-2-rc3:run \
 *       -Drun.functionTarget=com.example.function.Echo}
 * </pre>
 *
 */
@Mojo(name = "run",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    requiresDependencyCollection = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.COMPILE)
public class RunFunction extends AbstractMojo {


  /**
   * The name of the function to run. This is the name of a class that implements one of the
   * interfaces in {@code com.google.cloud.functions}.
   */
  @Parameter(property = "run.functionTarget")
  private String functionTarget;

  /**
   * The port on which the HTTP server wrapping the function should listen.
   */
  @Parameter(property = "run.port", defaultValue = "8080")
  private Integer port;

  private final MavenSession mavenSession;
  private final MavenProject mavenProject;
  private final ProjectDependenciesResolver resolver;

  @Inject
  public RunFunction(MavenProject mavenProject, MavenSession mavenSession, ProjectDependenciesResolver resolver) {
    this.mavenProject = mavenProject;
    this.mavenSession = mavenSession;
    this.resolver = resolver;
  }

  public void execute() throws MojoExecutionException {
    String classpath = resolveDependencies();
    List<String> args = new ArrayList<>();
    args.addAll(Arrays.asList("--classpath", classpath));
    if (functionTarget != null) {
      args.addAll(Arrays.asList("--target", functionTarget));
    }
    try {
      getLog().info("Calling Invoker with " + args);
      Invoker.main(args.toArray(new String[0]));
    } catch (Exception e) {
      getLog().error("Could not invoke function: " + e, e);
      throw new MojoExecutionException("Could not invoke function", e);
    }
  }


  private String resolveDependencies() throws MojoExecutionException {
      DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE, JavaScopes.RUNTIME);
      RepositorySystemSession session = mavenSession.getRepositorySession();
      DependencyResolutionRequest dependencyResolutionRequest = new DefaultDependencyResolutionRequest(mavenProject, session);
      dependencyResolutionRequest.setResolutionFilter(filter);
    DependencyResolutionResult result;
    try {
      result = resolver.resolve(dependencyResolutionRequest);
      List<Dependency> dependencies = result.getDependencies();
      return buildClasspath(dependencies);
    } catch (DependencyResolutionException e) {
      throw new MojoExecutionException("Error resolving runtime classpath: " + e.getMessage(), e );
    }
  }

  private String buildClasspath(List<Dependency> dependencies) {
    Comparator<Dependency> byGroupId = Comparator.comparing(d -> d.getArtifact().getGroupId());
    Comparator<Dependency> byArtifactId = Comparator.comparing(d -> d.getArtifact().getArtifactId());
    return dependencies.stream()
            .sorted(byGroupId.thenComparing(byArtifactId))
            .map(dependency -> dependency.getArtifact().getFile().getAbsolutePath())
            .collect(Collectors.joining(File.pathSeparator));
  }
}
