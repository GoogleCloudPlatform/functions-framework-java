REPO_ROOT=$(git rev-parse --show-toplevel)

rm -rf $REPO_ROOT/tmp
mkdir $REPO_ROOT/tmp

cp -r $REPO_ROOT/invoker/conformance $REPO_ROOT/tmp

cd $REPO_ROOT/invoker
mvn install -Dmaven.repo.local=$REPO_ROOT/tmp/conformance/artifacts

cd $REPO_ROOT/functions-framework-api 
mvn install -Dmaven.repo.local=$REPO_ROOT/tmp/conformance/artifacts

rm $REPO_ROOT/tmp/conformance/pom.xml
mv $REPO_ROOT/tmp/conformance/buildpack.xml $REPO_ROOT/tmp/conformance/pom.xml

