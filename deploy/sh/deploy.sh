#!/bin/sh

if [[ "$(docker images ${IMAGE_NAME})" != "" ]];
  then
    docker rmi ${IMAGE_NAME}
fi

# docker login ${HARBOR_SERVER_IP} -u ${HARBOR_USER_NAME} -p ${HARBOR_PASSWORD};
# docker pull ${HARBOR_SERVER_IP}/${IMAGE_NAME};
docker pull ${IMAGE_NAME}

if [[ "$(docker ps -a | grep ${deployment.APP_NAME})" != "" ]];
  then
    docker rename ${deployment.APP_NAME} ${deployment.APP_NAME}-old
    docker stop ${deployment.APP_NAME}-old
fi

mkdir -p /home/logs/${deployment.APP_NAME}
docker run -d -p ${deployment.APP_PORT}:${deployment.APP_PORT} -v /etc/localtime:/etc/localtime -v /home/logs/${deployment.APP_NAME}:/logs -e TZ=Asia/Shanghai --name ${deployment.APP_NAME} ${IMAGE_NAME}

if [[ "$(docker ps -a | grep ${deployment.APP_NAME}-old)" != "" ]];
  then
    docker rm ${deployment.APP_NAME}-old
fi