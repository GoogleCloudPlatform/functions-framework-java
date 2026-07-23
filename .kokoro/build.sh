#!/bin/bash
set -euo pipefail

# The repo is cloned to $KOKORO_ARTIFACTS_DIR/git/functions-framework-java
REPO_DIR="${KOKORO_ARTIFACTS_DIR}/git/functions-framework-java"
cd "${REPO_DIR}"

# ==============================================================================
# 0. Set up Java 17 Environment
# ==============================================================================
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="${JAVA_HOME}/bin:${PATH}"

# ==============================================================================
# 1. Configure Airlock and AR Credentials
# ==============================================================================
# Get OAuth token from GCE metadata server inside Kokoro VM
MAVEN_TOKEN=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" -H "Metadata-Flavor: Google" | grep -oP '"access_token":"\K[^"]+')

# Create a temporary settings.xml to configure Airlock mirror and AR auth
cat > settings.xml <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <!-- Redirect ALL downloads to Airlock Maven Central mirror -->
    <mirror>
      <id>airlock-mirror</id>
      <name>Airlock Maven Central mirror</name>
      <url>https://us-maven.pkg.dev/artifact-foundry-prod/maven-3p-trusted</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
  <servers>
    <!-- Credentials for the Airlock mirror -->
    <server>
      <id>airlock-mirror</id>
      <username>oauth2accesstoken</username>
      <password>${MAVEN_TOKEN}</password>
    </server>
    <!-- Credentials for the Exit Gate AR deployment repo -->
    <server>
      <id>exit-gate-ar</id>
      <username>oauth2accesstoken</username>
      <password>${MAVEN_TOKEN}</password>
    </server>
  </servers>
</settings>
EOF

# ==============================================================================
# 2. Retrieve GPG keys from Secret Manager
# ==============================================================================
GPG_KEYRING="${KOKORO_ARTIFACTS_DIR}/gpg-keyring"
GPG_PASSPHRASE_FILE="${KOKORO_ARTIFACTS_DIR}/gpg-passphrase"

# Read names from environment variables injected by Louhi
PROJECT_ID="${_LOUHI_SECRET_PROJECT_ID}"
KEYRING_NAME="${_LOUHI_GPG_KEYRING_SECRET_NAME}"
PASSPHRASE_NAME="${_LOUHI_GPG_PASSPHRASE_SECRET_NAME}"

echo "Fetching secrets from project: ${PROJECT_ID}"
gcloud secrets versions access latest --secret="${KEYRING_NAME}" --project="${PROJECT_ID}" > "${GPG_KEYRING}"
gcloud secrets versions access latest --secret="${PASSPHRASE_NAME}" --project="${PROJECT_ID}" > "${GPG_PASSPHRASE_FILE}"

export GPG_TTY=$(tty)
export GPG_PASSPHRASE=$(cat "${GPG_PASSPHRASE_FILE}")
export GNUPGHOME=/tmp/gpg
mkdir -p "${GNUPGHOME}"
gpg --batch --import "${GPG_KEYRING}"

# ==============================================================================
# 3. Build, Sign, and Deploy
# ==============================================================================
# Detect which package to build based on the Louhi trigger tag
if [[ -n "${_LOUHI_REF_NAME:-}" ]]; then
  echo "Triggered by Louhi tag: ${_LOUHI_REF_NAME}"
  if [[ "${_LOUHI_REF_NAME}" == *functions-framework-api* ]]; then
    PACKAGE_DIR="functions-framework-api"
  elif [[ "${_LOUHI_REF_NAME}" == *function-maven-plugin* ]]; then
    PACKAGE_DIR="function-maven-plugin"
  elif [[ "${_LOUHI_REF_NAME}" == *java-function-invoker* ]]; then
    PACKAGE_DIR="invoker"
  else
    echo "Unknown tag format: ${_LOUHI_REF_NAME}. Defaulting to invoker."
    PACKAGE_DIR="invoker"
  fi
else
  # Fallback for manual/non-tag builds (e.g. testing)
  echo "No Louhi tag detected. Falling back to KOKORO_JOB_NAME detection."
  if [[ $KOKORO_JOB_NAME == *"function-maven-plugin"* ]]; then
    PACKAGE_DIR="function-maven-plugin"
  elif [[ $KOKORO_JOB_NAME == *"functions-framework-api"* ]]; then
    PACKAGE_DIR="functions-framework-api"
  else
    PACKAGE_DIR="invoker"
  fi
fi

echo "Building package in directory: ${PACKAGE_DIR}"
cd "${PACKAGE_DIR}"

# Pre-emptively clear any stale un-promoted staging version under builder service account permissions
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo "Pre-clearing stale staging version ${VERSION} from exit-gate-ar..."

if [[ "${PACKAGE_DIR}" == "invoker" ]]; then
  gcloud artifacts versions delete "${VERSION}" --project=oss-exit-gate-prod --repository=ff-releases--mavencentral --location=us --package="com.google.cloud.functions.invoker:java-function-invoker-parent" --quiet || true
  gcloud artifacts versions delete "${VERSION}" --project=oss-exit-gate-prod --repository=ff-releases--mavencentral --location=us --package="com.google.cloud.functions.invoker:java-function-invoker" --quiet || true
  gcloud artifacts versions delete "${VERSION}" --project=oss-exit-gate-prod --repository=ff-releases--mavencentral --location=us --package="com.google.cloud.functions.invoker:conformance" --quiet || true
else
  gcloud artifacts versions delete "${VERSION}" --project=oss-exit-gate-prod --repository=ff-releases--mavencentral --location=us --package="com.google.cloud.functions:${PACKAGE_DIR}" --quiet || true
fi

# Run maven deploy using the temporary settings.xml
# We use altDeploymentRepository to override the deploy target without editing pom.xml
mvn clean deploy -B \
  -P sonatype-oss-release \
  --settings=../settings.xml \
  -DaltDeploymentRepository=exit-gate-ar::https://us-maven.pkg.dev/oss-exit-gate-prod/ff-releases--mavencentral \
  -DskipPublishing=true \
  -Dcentral.skipPublishing=true \
  -Dgpg.executable=gpg \
  -Dgpg.passphrase="${GPG_PASSPHRASE}" \
  -Dgpg.homedir="${GNUPGHOME}"

# ==============================================================================
# 4. Copy artifacts to 'artifacts/' folder for Kokoro Attestation Generation
# ==============================================================================
ARTIFACTS_DIR="${KOKORO_ARTIFACTS_DIR}/artifacts"
mkdir -p "${ARTIFACTS_DIR}"

# Copy target jars and poms (excluding test jars) to be captured by build.cfg
# Copy target jars and poms from all module target/ folders across the package hierarchy,
# while explicitly excluding unit test artifacts (test-classes/, *-tests.jar, *-test-sources.jar)
find . -path "*/target/*" \
  \( -name "*.jar" -o -name "*.pom" \) \
  ! -path "*/test-classes/*" \
  ! -name "*-tests.jar" \
  ! -name "*-test-sources.jar" \
  | xargs -r -I {} cp {} "${ARTIFACTS_DIR}/"
