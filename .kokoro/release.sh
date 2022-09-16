#!/bin/bash

# Stop execution when any command fails.
set -e

# Get secrets from keystore and set and environment variables.
setup_environment_secrets() {
  export SONATYPE_USERNAME=functions-framework-release-bot
  export SONATYPE_PASSWORD=$(cat ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-sonatype-password)
  export GPG_PASSPHRASE=$(cat ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-gpg-passphrase)

  # Add the keybox file to $GNUPGHOME to verify the GPG credentials.
  export GNUPGHOME=/tmp/gpg
  mkdir $GNUPGHOME
  mv ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-gpg-pubring $GNUPGHOME/pubring.kbx
  # Restart the gpg-agent to avoid the error of no default secret key.
  gpg2 -k
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
  -Dgpg.executable=gpg2 \
  -Dgpg.passphrase=${GPG_PASSPHRASE} \
  -Dgpg.homedir=${GNUPGHOME}
