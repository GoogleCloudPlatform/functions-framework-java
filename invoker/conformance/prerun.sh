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
# - Build java-function-invoker with version 1.1.1-SNAPSHOT into artifacts
#  folder
# - Build functions-framework-api with version 1.0.5-SNAPSHOT into artifacts 
# folder
# - Ensure that we use the buildpack_pom.xml file by renaming it to pom.xml


set -e
REPO_ROOT=$(git rev-parse --show-toplevel)

rm -rf $REPO_ROOT/tmp
mkdir $REPO_ROOT/tmp

cp -r $REPO_ROOT/invoker/conformance $REPO_ROOT/tmp

cd $REPO_ROOT/invoker
mvn install -Dmaven.repo.local=$REPO_ROOT/tmp/conformance/artifacts

cd $REPO_ROOT/functions-framework-api 
mvn install -Dmaven.repo.local=$REPO_ROOT/tmp/conformance/artifacts

rm $REPO_ROOT/tmp/conformance/pom.xml
mv $REPO_ROOT/tmp/conformance/buildpack_pom.xml $REPO_ROOT/tmp/conformance/pom.xml
