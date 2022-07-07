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

rm -rf $TMPDIR/tests
mkdir $TMPDIR/tests

cp -r $REPO_ROOT/invoker/conformance $TMPDIR/tests

cd $REPO_ROOT/invoker
mvn versions:set -DnewVersion=0.0.0-SNAPSHOT -DprocessAllModules=true
mvn install -Dmaven.repo.local=$TMPDIR/tests/conformance/artifacts

cd $REPO_ROOT/functions-framework-api 
mvn versions:set -DnewVersion=0.0.0-SNAPSHOT -DprocessAllModules=true
mvn install -Dmaven.repo.local=$TMPDIR/tests/conformance/artifacts

rm $TMPDIR/tests/conformance/pom.xml
mv $TMPDIR/tests/conformance/buildpack_pom.xml $TMPDIR/tests/conformance/pom.xml
