package com.google.cloud.functions.plugin;

import com.google.cloud.tools.appengine.operations.CloudSdk;
import com.google.cloud.tools.appengine.operations.Gcloud;
import com.google.cloud.tools.appengine.operations.cloudsdk.CloudSdkNotFoundException;
import com.google.cloud.tools.appengine.operations.cloudsdk.CloudSdkOutOfDateException;
import com.google.cloud.tools.appengine.operations.cloudsdk.CloudSdkVersionFileException;
import com.google.cloud.tools.appengine.operations.cloudsdk.process.ProcessHandlerException;
import com.google.cloud.tools.managedcloudsdk.BadCloudSdkVersionException;
import com.google.cloud.tools.managedcloudsdk.ManagedCloudSdk;
import com.google.cloud.tools.managedcloudsdk.UnsupportedOsException;
import com.google.cloud.tools.managedcloudsdk.Version;
import com.google.cloud.tools.maven.cloudsdk.CloudSdkChecker;
import com.google.cloud.tools.maven.cloudsdk.CloudSdkDownloader;
import com.google.cloud.tools.maven.cloudsdk.CloudSdkMojo;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Deploy a Java function via mvn functions:deploy with optional flags. */
@Mojo(
    name = "deploy",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresDependencyResolution = ResolutionScope.NONE,
    requiresDependencyCollection = ResolutionScope.NONE)
@Execute(phase = LifecyclePhase.NONE)
public class DeployFunction extends CloudSdkMojo {

  /** The Google Cloud Platform project Id to use for this invocation. */
  @Parameter(alias = "deploy.projectId", property = "function.deploy.projectId")
  protected String projectId;

  /**
   * ID of the function or fully qualified identifier for the function. This property must be
   * specified if any of the other arguments in this group are specified.
   */
  @Parameter(alias = "deploy.name", property = "function.deploy.name", required = true)
  String name;

  /**
   * The Cloud region for the function. Overrides the default functions/region property value for
   * this command invocation.
   */
  @Parameter(alias = "deploy.region", property = "function.deploy.region")
  String region;

  /**
   * If set, makes this a public function. This will allow all callers, without checking
   * authentication.
   */
  @Parameter(
      alias = "deploy.allowunauthenticated",
      property = "function.deploy.allowunauthenticated")
  Boolean allowUnauthenticated;

  /**
   * Name of a Google Cloud Function (as defined in source code) that will be executed. Defaults to
   * the resource name suffix, if not specified.
   *
   * <p>For Java this is fully qualified class name implementing the function, for example
   * `com.google.testfunction.HelloWorld`.
   */
  @Parameter(alias = "deploy.functiontarget", property = "function.deploy.functiontarget")
  String functionTarget;

  /** Override the .gcloudignore file and use the specified file instead. */
  @Parameter(alias = "deploy.ignorefile", property = "function.deploy.ignorefile")
  String ignoreFile;

  /**
   * Limit on the amount of memory the function can use.
   *
   * <p>Allowed values are: 128MB, 256MB, 512MB, 1024MB, and 2048MB. By default, a new function is
   * limited to 256MB of memory. When deploying an update to an existing function, the function will
   * keep its old memory limit unless you specify this flag.
   */
  @Parameter(alias = "deploy.memory", property = "function.deploy.memory")
  String memory;

  /** If specified, then the function will be retried in case of a failure. */
  @Parameter(alias = "deploy.retry", property = "function.deploy.retry")
  String retry;

  /**
   * Runtime in which to run the function.
   *
   * <p>Required when deploying a new function; optional when updating an existing function. Default
   * to Java11.
   */
  @Parameter(
      alias = "deploy.runtime",
      defaultValue = "java11",
      property = "function.deploy.runtime")
  String runtime = "java11";

  /**
   * The email address of the IAM service account associated with the function at runtime. The
   * service account represents the identity of the running function, and determines what
   * permissions the function has.
   *
   * <p>If not provided, the function will use the project's default service account.
   */
  @Parameter(alias = "deploy.serviceaccount", property = "function.deploy.serviceaccount")
  String serviceAccount;

  /** Location of source code to deploy. */
  @Parameter(alias = "deploy.source", property = "function.deploy.source")
  String source;

  /**
   * This flag's value is the name of the Google Cloud Storage bucket in which source code will be
   * stored.
   */
  @Parameter(alias = "deploy.stagebucket", property = "function.deploy.stagebucket")
  String stageBucket;

  /**
   * The function execution timeout, e.g. 30s for 30 seconds. Defaults to original value for
   * existing function or 60 seconds for new functions. Cannot be more than 540s.
   */
  @Parameter(alias = "deploy.timeout", property = "function.deploy.timeout")
  String timeout;

  /**
   * List of label KEY=VALUE pairs to update. If a label exists its value is modified, otherwise a
   * new label is created.
   */
  @Parameter(alias = "deploy.updatelabels", property = "function.deploy.updatelabels")
  List<String> updateLabels;

  /**
   * Function will be assigned an endpoint, which you can view by using the describe command. Any
   * HTTP request (of a supported type) to the endpoint will trigger function execution. Supported
   * HTTP request types are: POST, PUT, GET, DELETE, and OPTIONS.
   */
  @Parameter(alias = "deploy.triggerhttp", property = "function.deploy.triggerhttp")
  Boolean triggerHttp;

  /**
   * Name of Pub/Sub topic. Every message published in this topic will trigger function execution
   * with message contents passed as input data.
   */
  @Parameter(alias = "deploy.triggertopic", property = "function.deploy.triggertopic")
  String triggerTopic;

  /**
   * Specifies which action should trigger the function. For a list of acceptable values, call
   * gcloud functions event-types list.
   */
  @Parameter(alias = "deploy.triggerevent", property = "function.deploy.triggerevent")
  String triggerEvent;

  /**
   * Specifies which resource from {@link #triggerEvent} is being observed. E.g. if {@link
   * #triggerEvent} is providers/cloud.storage/eventTypes/object.change, {@link #triggerResource}
   * must be a bucket name. For a list of expected resources, run {@code gcloud functions
   * event-types list}.
   */
  @Parameter(alias = "deploy.triggerresource", property = "function.deploy.triggerresource")
  String triggerResource;

  /**
   * The VPC Access connector that the function can connect to. It can be either the fully-qualified
   * URI, or the short name of the VPC Access connector resource. If the short name is used, the
   * connector must belong to the same project. The format of this field is either
   * projects/${PROJECT}/locations/${LOCATION}/connectors/${CONNECTOR} or ${CONNECTOR}, where
   * ${CONNECTOR} is the short name of the VPC Access connector.
   */
  @Parameter(alias = "deploy.vpcconnector", property = "function.deploy.vpcconnector")
  String vpcConnector;

  /**
   * Sets the maximum number of instances for the function. A function execution that would exceed
   * max-instances times out.
   */
  @Parameter(alias = "deploy.maxinstances", property = "function.deploy.maxinstances")
  Integer maxInstances;

  /**
   * List of key-value pairs to set as environment variables. All existing environment variables
   * will be removed first.
   */
  @Parameter(alias = "deploy.setenvvars", property = "function.deploy.setenvvars")
  Map<String, String> environmentVariables;

  /**
   * Path to a local YAML file with definitions for all environment variables. All existing
   * environment variables will be removed before the new environment variables are added.
   */
  @Parameter(alias = "deploy.envvarsfile", property = "function.deploy.envvarsfile")
  String envVarsFile;

  /**
   * List of key-value pairs to set as build environment variables. All existing environment
   * variables will be removed first.
   */
  @Parameter(alias = "deploy.setbuildenvvars", property = "function.deploy.setbuildenvvars")
  Map<String, String> buildEnvironmentVariables;

  /**
   * Path to a local YAML file with definitions for all build environment variables. All existing
   * environment variables will be removed before the new environment variables are added.
   */
  @Parameter(alias = "deploy.buildenvvarsfile", property = "function.deploy.buildenvvarsfile")
  String buildEnvVarsFile;

  /** If true, deploys the function in the 2nd Generation Environment. */
  @Parameter(alias = "deploy.gen2", property = "function.deploy.gen2", defaultValue = "false")
  boolean gen2;

  boolean hasEnvVariables() {
    return (this.environmentVariables != null && !this.environmentVariables.isEmpty());
  }

  boolean hasBuildEnvVariables() {
    return (this.buildEnvironmentVariables != null && !this.buildEnvironmentVariables.isEmpty());
  }

  // Select a downloaded Cloud SDK or a user defined Cloud SDK version.
  static Function<String, ManagedCloudSdk> newManagedSdkFactory() {
    return version -> {
      try {
        if (Strings.isNullOrEmpty(version)) {
          return ManagedCloudSdk.newManagedSdk();
        } else {
          return ManagedCloudSdk.newManagedSdk(new Version(version));
        }
      } catch (UnsupportedOsException | BadCloudSdkVersionException ex) {
        throw new RuntimeException(ex);
      }
    };
  }

  CloudSdk buildCloudSdkMinimal() {
    return buildCloudSdk(
        (CloudSdkMojo) this, new CloudSdkChecker(), new CloudSdkDownloader(newManagedSdkFactory()));
  }

  static CloudSdk buildCloudSdk(
      CloudSdkMojo mojo, CloudSdkChecker cloudSdkChecker, CloudSdkDownloader cloudSdkDownloader) {

    try {
      if (mojo.getCloudSdkHome() != null) {
        // Check if the user has defined a specific Cloud SDK.
        CloudSdk cloudSdk = new CloudSdk.Builder().sdkPath(mojo.getCloudSdkHome()).build();

        if (mojo.getCloudSdkVersion() != null) {
          cloudSdkChecker.checkCloudSdk(cloudSdk, mojo.getCloudSdkVersion());
        }

        return cloudSdk;
      } else {

        return new CloudSdk.Builder()
            .sdkPath(
                cloudSdkDownloader.downloadIfNecessary(
                    mojo.getCloudSdkVersion(),
                    mojo.getLog(),
                    Collections.emptyList(),
                    mojo.getMavenSession().isOffline()))
            .build();
      }
    } catch (CloudSdkNotFoundException
        | CloudSdkOutOfDateException
        | CloudSdkVersionFileException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Return a Gcloud instance using global configuration. */
  public Gcloud getGcloud() {
    return Gcloud.builder(buildCloudSdkMinimal())
        .setMetricsEnvironment(this.getArtifactId(), this.getArtifactVersion())
        .setCredentialFile(this.getServiceAccountKeyFile())
        .build();
  }

  /** Return the list of command parameters to give to the Cloud SDK for execution */
  public List<String> getCommands() {
    List<String> commands = new ArrayList<>();

    commands.add("functions");
    commands.add("deploy");
    commands.add(name);
    if (gen2) {
      commands.add("--gen2");
    }
    if (region != null) {
      commands.add("--region=" + region);
    }
    if (triggerResource == null && triggerTopic == null && triggerEvent == null) {
      commands.add("--trigger-http");
    }
    if (triggerResource != null) {
      commands.add("--trigger-resource=" + triggerResource);
    }
    if (triggerTopic != null) {
      commands.add("--trigger-topic=" + triggerTopic);
    }
    if (triggerEvent != null) {
      commands.add("--trigger-event=" + triggerEvent);
    }
    if (allowUnauthenticated != null) {
      if (allowUnauthenticated) {
        commands.add("--allow-unauthenticated");
      } else {
        commands.add("--no-allow-unauthenticated");
      }
    }
    if (functionTarget != null) {
      commands.add("--entry-point=" + functionTarget);
    }
    if (ignoreFile != null) {
      commands.add("--ignore-file=" + ignoreFile);
    }
    if (memory != null) {
      commands.add("--memory=" + memory);
    }
    if (retry != null) {
      commands.add("--retry=" + retry);
    }
    if (serviceAccount != null) {
      commands.add("--service-account=" + serviceAccount);
    }
    if (source != null) {
      commands.add("--source=" + source);
    }
    if (stageBucket != null) {
      commands.add("--stage-bucket=" + stageBucket);
    }
    if (timeout != null) {
      commands.add("--timeout=" + timeout);
    }
    if (updateLabels != null && !updateLabels.isEmpty()) {
      commands.add("--update-labels=" + String.join(",", updateLabels));
    }

    if (vpcConnector != null) {
      commands.add("--vpc-connector=" + vpcConnector);
    }
    if (maxInstances != null) {
      commands.add("--max-instances=" + maxInstances);
    }

    if (hasEnvVariables()) {
      Joiner.MapJoiner mapJoiner = Joiner.on(",").withKeyValueSeparator("=");
      commands.add("--set-env-vars=" + mapJoiner.join(environmentVariables));
    }
    if (envVarsFile != null) {
      commands.add("--env-vars-file=" + envVarsFile);
    }
    if (hasBuildEnvVariables()) {
      Joiner.MapJoiner mapJoiner = Joiner.on(",").withKeyValueSeparator("=");
      commands.add("--set-build-env-vars=" + mapJoiner.join(buildEnvironmentVariables));
    }
    if (buildEnvVarsFile != null) {
      commands.add("--build-env-vars-file=" + buildEnvVarsFile);
    }
    commands.add("--runtime=" + runtime);

    if (projectId != null) {
      commands.add("--project=" + projectId);
    }

    commands.add("--quiet");
    return Collections.unmodifiableList(commands);
  }

  @Override
  public void execute() throws MojoExecutionException {
    try {
      Gcloud gcloud = getGcloud();
      List<String> params = getCommands();
      System.out.println("Executing Cloud SDK command: gcloud " + String.join(" ", params));
      gcloud.runCommand(params);
    } catch (CloudSdkNotFoundException | IOException | ProcessHandlerException ex) {
      Logger.getLogger(DeployFunction.class.getName()).log(Level.SEVERE, "Function deployment failed", ex);
      throw new MojoExecutionException("Function deployment failed", ex);
    }
  }
}
