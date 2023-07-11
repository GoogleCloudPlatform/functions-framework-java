#!/bin/bash

# This script is used for buildpack validation.
# Its purpose is to run conformance tests using a buildpack
# with the latest code of functions-framework.
#
# Note: buildpack_pom.xml contains the configuration to use the local versions
# java-function-invoker and functions-framework-api. We will be using this file
# for running our conformance tests with buildpack.
#
# Steps:
#
# - Clear out the temp directory
# - Copy the conformance tests folder into temp
# - Build java-function-invoker with version 0.0.0-SNAPSHOT into artifacts
#  folder
# - Build functions-framework-api with version 0.0.0-SNAPSHOT into artifacts
# folder
# - Ensure that we use the buildpack_pom.xml file by renaming it to pom.xml

set -e
REPO_ROOT=$(git rev-parse --show-toplevel)

rm -rf /tmp/tests
mkdir /tmp/tests

cp -r $REPO_ROOT/invoker/conformance /tmp/tests

function get_mvn_version() {
  mvn -q \
    -Dexec.executable=echo \
    -Dexec.args='${project.version}' \
    --non-recursive \
    exec:exec
}

# Must first install a local version of the API package
cd $REPO_ROOT/functions-framework-api
mvn install -Dmaven.repo.local=/tmp/tests/conformance/artifacts
FRAMEWORK_API_VERSION=$(get_mvn_version)

# Build invoker packages against the latest API package
cd $REPO_ROOT/invoker
mvn install -Dmaven.repo.local=/tmp/tests/conformance/artifacts
INVOKER_VERSION=$(get_mvn_version)

rm /tmp/tests/conformance/pom.xml
mv /tmp/tests/conformance/buildpack_pom.xml /tmp/tests/conformance/pom.xml

sed -i "s/FRAMEWORK-API-VERSION/${FRAMEWORK_API_VERSION}/g" /tmp/tests/conformance/pom.xml
sed -i "s/INVOKER-VERSION/${INVOKER_VERSION}/g" /tmp/tests/conformance/pom.xml

cat /tmp/tests/conformance/pom.xml
