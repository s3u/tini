#usr/bin/env bash
function die() {
  echo $*
  exit 1
}

fullPath=`dirname $0`
jar=`find $fullPath/target/*with-dependencies.jar`
cp=`echo $jar | sed 's,./,'$fullPath'/,'`
#javaArgs="-server -cp "$cp" $*"
javaArgs="-Djava.util.logging.config.file=logging.properties -server -Xmx2048m -cp "$cp" -agentpath:/home/subbu/netbeans-6.9.1/profiler/lib/deployed/jdk16/linux-amd64/libprofilerinterface.so=/home/subbu/netbeans-6.9.1/profiler/lib,5140 $*"

if [ $# -eq 0 ]
then
        echo "Specify the fully qualified class name to run"
        exit 1
fi

echo "Running using Java on path at `which java` with args $javaArgs"
java $debugArgs $javaArgs $1 || die "Java process exited abnormally"
#java -Dhttp.proxyHost=localhost -Dhttp.proxyPort=8888 $debugArgs $javaArgs $1 || die "Java process exited abnormally"
