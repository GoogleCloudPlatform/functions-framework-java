package com.google.cloud.functions.plugin;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeployFunctionTest {

  @Test
  public void testDeployFunctionCommandLine() {
    DeployFunction mojo = new DeployFunction();
    mojo.envVarsFile = "myfile";
    mojo.buildEnvVarsFile = "myfile2";
    mojo.functionTarget = "function";
    mojo.ignoreFile = "ff";
    mojo.maxInstances = new Integer(3);
    mojo.memory = "234";
    mojo.name = "a name";
    mojo.region = "a region";
    mojo.retry = "44";
    mojo.source = "a source";
    mojo.stageBucket = "a bucket";
    mojo.timeout = "timeout";
    mojo.vpcConnector = "a connector";
    mojo.triggerHttp = true;
    mojo.allowUnauthenticated = true;
    mojo.environmentVariables = ImmutableMap.of("env1", "a", "env2", "b");
    mojo.buildEnvironmentVariables = ImmutableMap.of("env1", "a", "env2", "b");
    List<String> expected =
        ImmutableList.of(
            "functions",
            "deploy",
            "a name",
            "--region=a region",
            "--trigger-http",
            "--allow-unauthenticated",
            "--entry-point=function",
            "--ignore-file=ff",
            "--memory=234",
            "--retry=44",
            "--source=a source",
            "--stage-bucket=a bucket",
            "--timeout=timeout",
            "--vpc-connector=a connector",
            "--max-instances=3",
            "--set-env-vars=env1=a,env2=b",
            "--env-vars-file=myfile",
            "--set-build-env-vars=env1=a,env2=b",
            "--build-env-vars-file=myfile2",
            "--runtime=java11",
            "--quiet");
    assertThat(mojo.getCommands()).isEqualTo(expected);
  }
}
