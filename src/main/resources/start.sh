#!/bin/bash


ENV=dev
RUNNING_USER=wayne
ADATE=`date +%Y%m%d%H%M%S`
APP_NAME=$1
ENABLE_COPY_CONFIG_TO_SYSTEM=$2



APP_HOME=`pwd`
echo $APP_HOME
dirname $0|grep "^/" >/dev/null
if [[ $? -eq 0 ]];then
   APP_HOME=`dirname $0`
else
    dirname $0|grep "^\." >/dev/null
    retval=$?
    if [[ ${retval} -eq 0 ]];then
        APP_HOME=`dirname $0|sed "s#^.#$APP_HOME#"`
    else
        APP_HOME=`dirname $0|sed "s#^#$APP_HOME/#"`
    fi
fi

if [[ ! -d "$APP_HOME/../logs" ]];then
  mkdir ${APP_HOME}/../logs
fi






getFileName() {
#    echo $1
    path=$1
    files=$(ls $path | grep application-*)
    for filename in $files
    do
     echo $filename
     APP_CONFIG_NAME=$filename
    done
}

CONFIG_PATH="${APP_HOME}/../config/"

# 配置文件名
APP_CONFIG_NAME=application-override.properties
getFileName ${CONFIG_PATH}
APP_JAR_CONFIG=classpath:/application.yaml,classpath:/application-dev.yaml,classpath:/application-prod.yaml
#APP_INNER_CONFIG=`${APP_HOME}/../config/application-override.yaml,${APP_HOME}/../config/${APP_CONFIG_NAME}`
APP_INNER_CONFIG="config/application-override.yaml,config/${APP_CONFIG_NAME}"

LOG_PATH=logs/${APP_NAME}.out
GC_LOG_PATH=${APP_HOME}/../logs/gc-${APP_NAME}-${ADATE}.log
#JMX监控需用到
JMX="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=1091 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"
#JVM参数
JVM_OPTS="-Dname=$APP_NAME -Djeesuite.configcenter.profile=$ENV -Duser.timezone=Asia/Shanghai -Xms512M -Xmx512M -XX:PermSize=256M -XX:MaxPermSize=512M -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDateStamps -Xloggc:$GC_LOG_PATH -XX:+PrintGCDetails -XX:NewRatio=1 -XX:SurvivorRatio=30 -XX:+UseParallelGC -XX:+UseParallelOldGC"
CONFIG_LOCATION="--spring.config.location=${APP_JAR_CONFIG},${APP_INNER_CONFIG}"

if [[ ${ENABLE_COPY_CONFIG_TO_SYSTEM} == true && $3 == "start" ]]; then
    echo "开始拷贝配置文件。。。"
    # 创建用户目录下配置文件
    if [[ ! -d "${HOME}/.m2micro/${APP_NAME}/config" ]];then
        $(mkdir -p "${HOME}/.m2micro/${APP_NAME}/config")
    fi
    # 拷贝配置文件
    APP_CONFIG_HOME="${HOME}/.m2micro/${APP_NAME}/config"

    if [[ "`ls -A $APP_CONFIG_HOME`" = "" ]]; then
        $(cp ${APP_HOME}/../config/application-*.properties ${APP_CONFIG_HOME})
    fi

    APP_OUTER_CONFIG="${APP_CONFIG_HOME}/${APP_CONFIG_NAME}"
    CONFIG_LOCATION="${CONFIG_LOCATION},${APP_OUTER_CONFIG}"
fi

JAR_FILE=bin/${APP_NAME}.jar
pid=0




start(){
  checkpid
  if [[ ! -n "$pid" ]]; then
#    JAVA_CMD="nohup java -jar $JVM_OPTS $JAR_FILE > $LOG_PATH 2>&1 &"
#    su - ${RUNNING_USER} -c "$JAVA_CMD"
    nohup java -jar ${JAR_FILE} ${CONFIG_LOCATION} > ${LOG_PATH} 2>&1 &
#    echo "nohup java -jar ${JAR_FILE} ${CONFIG_LOCATION} > ${LOG_PATH} 2>&1 &"
    echo "---------------------------------"
    echo "启动完成，按CTRL+C退出日志界面即可>>>>>"
    echo "---------------------------------"
    sleep 1s
    tail -f ${LOG_PATH}
  else
      echo "$APP_NAME is runing PID: $pid"
  fi

}


status(){
   checkpid
   if [[ ! -n "$pid" ]]; then
     echo "$APP_NAME not runing"
   else
     echo "$APP_NAME runing PID: $pid"
   fi
}

showLog() {
    checkpid
   if [[ ! -n "$pid" ]]; then
     echo "$APP_NAME not runing"
   else
     tail -f ${LOG_PATH}
   fi
}

checkpid(){
    pid=`ps -ef |grep ${JAR_FILE} |grep -v grep |awk '{print $2}'`
}

stop(){
    checkpid
    if [[ ! -n "$pid" ]]; then
     echo "$APP_NAME not runing"
    else
      echo "$APP_NAME stop..."
      kill -9 $pid
    fi
}

restart(){
    stop
    sleep 1s
    start
}

case $3 in
          start) start;;
          stop)  stop;;
          restart)  restart;;
          status)  status;;
          log) showLog;;
              *)  echo "require start|stop|restart|status"  ;;
esac
