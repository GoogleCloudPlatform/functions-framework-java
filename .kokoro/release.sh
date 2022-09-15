#!/bin/bash

# Get secrets from keystore and set and environment variables.
setup_environment_secrets() {
  export SONATYPE_USERNAME=functions-framework-release-bot
  export SONATYPE_PASSWORD=$(cat ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-sonatype-password)
  export GPG_PASSPHRASE=$(cat ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-gpg-passphrase)

  export GNUPGHOME=/tmp/gpg
  mkdir $GNUPGHOME
  mv ${KOKORO_KEYSTORE_DIR}/75669_functions-framework-java-release-bot-gpg-pubring $GNUPGHOME/pubring.kbx
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
create_settings_xml_file "settings.xml"

# Pick the right package to release based on the Kokoro job name.
cd ${KOKORO_ARTIFACTS_DIR}/github/functions-framework-java
echo "KOKORO_JOB_NAME=${KOKORO_JOB_NAME}"
if [[ $KOKORO_JOB_NAME == *"function-maven-plugin"* ]]; then
  cd function-maven-plugin
elif [[ $KOKORO_JOB_NAME == *"functions-framework-api"* ]]; then
  cd functions-framework-api
else
  cd invoker
fi

# Make sure `JAVA_HOME` is set and using jdk11.
sudo update-java-alternatives --set java-1.11.0-openjdk-amd64
echo "JAVA_HOME=$JAVA_HOME"
mvn clean deploy -P sonatype-oss-release
