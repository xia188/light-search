#!/bin/sh

daemon=false
appname=light-search
jarfile=target/$appname.jar
[ ! -e "$jarfile" ] && jarfile=$appname.jar
Survivor=2 Old=32 NewSize=$[Survivor*10] Xmx=$[NewSize+Old] #NewSize=Survivor*(1+1+8) Xmx=NewSize+Old
JVM_OPS="-Xmx${Xmx}m -Xms${Xmx}m -XX:NewSize=${NewSize}m -XX:MaxNewSize=${NewSize}m -XX:SurvivorRatio=8 -Xss228k"
JVM_OPS="$JVM_OPS -Djava.compiler=none -Dlogserver -Dlogserver.token=xlongwei -DcontextName=$appname"
#ENV_OPS="PATH=/usr/java/jdk1.8.0_161/bin:$PATH"
JVM_OPS="$JVM_OPS -Duser.timezone=GMT+8 -DclientThreads=1"
#JVM_OPS="$JVM_OPS -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n"
ENV_OPS="$ENV_OPS httpPort=9200 ioThreads=2 workerThreads=3"
ENV_OPS="$ENV_OPS light4j.host=http://localhost:8081"

usage(){
    echo "Usage: start.sh ( commands ... )"
    echo "commands: "
    echo "  status      check the running status"
    echo "  start       start $appname"
    echo "  stop        stop $appname"
    echo "  restart     stop && start"
    echo "  clean       clean target"
    echo "  jar         build $jarfile"
    echo "  jars        copy dependencies to target"
    echo "  package     jar && jars"
    echo "  rebuild     stop && jar && start"
    echo "  refresh     stop && clean && jar && jars && start"
    echo "  deploy      package fat-jar $jarfile"
    echo "  redeploy    package fat-jar $jarfile and restart"
}

status(){
    PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`

	if [ -z "$PIDS" ]; then
	    echo "$appname is not running!"
	else
		for PID in $PIDS ; do
		    echo "$appname has pid: $PID!"
		done
	fi
}

stop(){
    PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`

	if [ -z "$PIDS" ]; then
	    echo "$appname is not running!"
	else
		echo -e "Stopping $appname ..."
		for PID in $PIDS ; do
			echo -e "kill $PID"
		    kill $PID > /dev/null 2>&1
		done
		COUNT=0
		while [ $COUNT -lt 1 ]; do
		    echo -e ".\c"
		    sleep 1
		    COUNT=1
		    for PID in $PIDS ; do
		        PID_EXIST=`ps -f -p $PID | grep "$jarfile"`
		        if [ -n "$PID_EXIST" ]; then
		            COUNT=0
		            break
		        fi
		    done
		done
	fi
}

clean(){
	mvn clean
}

jar(){
	mvn compile jar:jar
}

jars(){
	mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target
}

deploy(){
	mvn package -Prelease -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
}

start(){
	echo "starting $appname ..."
	JVM_OPS="-server -Djava.awt.headless=true $JVM_OPS"
	if [ "$daemon" = "true" ]; then
		env $ENV_OPS setsid java $JVM_OPS -jar $jarfile >> /dev/null 2>&1 &
	else
		env $ENV_OPS java $JVM_OPS -jar $jarfile 2>&1
	fi
}

if [ $# -eq 0 ]; then 
    usage
else
	case $1 in
	status) status ;;
	start) start ;;
	stop) stop ;;
	clean) clean ;;
	jar) jar ;;
	jars) jars ;;
	package) jar && jars ;;
	restart) stop && start ;;
	rebuild) stop && jar && start ;;
	refresh) stop && clean && jar && jars && start ;;
	deploy) deploy ;;
	redeploy) stop && deploy && start ;;
	*) usage ;;
	esac
fi
