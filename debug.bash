#!/usr/bin/env bash
function die() {
  echo $*
  exit 1
}

fullPath=`dirname $0`
jar=`find $fullPath/target/*with-dependencies.jar`
cp=`echo $jar | sed 's,./,'$fullPath'/,'`
#javaArgs="-server -cp "$cp" $*"
javaArgs="-Dlog4j.configuration=log4j.properties -server -Xmx2048m -cp "$cp" $*"

if [ $# -eq 0 ]
then
        echo "Specify the fully qualified class name to run"
        exit 1
fi

debugArgs="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

echo "Running using Java on path at `which java` with args $javaArgs"
java $debugArgs $javaArgs $1 || die "Java process exited abnormally"
