#!/bin/bash

# Stop execution when any command fails.
set -e

# update the Maven version to 3.6.3
pushd /usr/local
wget https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.6.3/apache-maven-3.6.3-bin.tar.gz
tar -xvzf apache-maven-3.6.3-bin.tar.gz apache-maven-3.6.3
rm -f /usr/local/apache-maven
ln -s /usr/local/apache-maven-3.6.3 /usr/local/apache-maven
rm apache-maven-3.6.3-bin.tar.gz
popd


# Get secrets from keystore and set and environment variables.
setup_environment_secrets() {
  export GPG_TTY=$(tty)
  export SONATYPE_USERNAME=$(cat ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-sonatype-password | cut -f1 -d':')
  export SONATYPE_PASSWORD=$(cat ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-sonatype-password | cut -f2 -d':')
  export GPG_PASSPHRASE=$(cat ${KOKORO_KEYSTORE_DIR}/70247_maven-gpg-passphrase)

  # Add the key ring files to $GNUPGHOME to verify the GPG credentials.
  export GNUPGHOME=/tmp/gpg
  mkdir $GNUPGHOME
  mv ${KOKORO_KEYSTORE_DIR}/70247_maven-gpg-pubkeyring $GNUPGHOME/pubring.gpg
  mv ${KOKORO_KEYSTORE_DIR}/70247_maven-gpg-keyring $GNUPGHOME/secring.gpg
  gpg -k
}

create_settings_xml_file() {
  echo "<settings>
   <profiles>
     <profile>
         <activation>
             <activeByDefault>true</activeByDefault>
         </activation>
         <properties>
             <gpg.passphrase>${GPG_PASSPHRASE}</gpg.passphrase>
         </properties>
     </profile>
  </profiles>
  <servers>
    <server>
      <id>sonatype-nexus-staging</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
    <server>
      <id>sonatype-nexus-snapshots</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
  </servers>
</settings>" > $1
}

setup_environment_secrets

# Pick the right package to release based on the Kokoro job name.
cd ${KOKORO_ARTIFACTS_DIR}/github/functions-framework-java
create_settings_xml_file "settings.xml"
echo "KOKORO_JOB_NAME=${KOKORO_JOB_NAME}"
if [[ $KOKORO_JOB_NAME == *"function-maven-plugin"* ]]; then
  cd function-maven-plugin
elif [[ $KOKORO_JOB_NAME == *"functions-framework-api"* ]]; then
  cd functions-framework-api
else
  cd invoker
fi
echo "pwd=$(pwd)"

# Make sure `JAVA_HOME` is set and using jdk11.
export JAVA_HOME=/usr/lib/jvm/java-1.11.0-openjdk-amd64
echo "JAVA_HOME=$JAVA_HOME"
mvn clean deploy -B \
  -P sonatype-oss-release \
  --settings=../settings.xml \
  -Dgpg.executable=gpg \
  -Dgpg.passphrase=${GPG_PASSPHRASE} \
  -Dgpg.homedir=${GNUPGHOME}
