##!/bin/sh

11111111111111

if [[ "$(docker images ${IMAGE_NAME})" != "" ]]
then
  docker rmi -f ${IMAGE_NAME}
fi

2222222222222

docker login ${HARBOR_SERVER_IP} -u ${HARBOR_USER_NAME} -p ${HARBOR_PASSWORD}
# docker pull ${HARBOR_SERVER_IP}/${IMAGE_NAME}
docker pull ${IMAGE_NAME}

33333333333

if [[ "$(docker ps -a | grep ${deployment.APP_NAME})" != "" ]]
then
  docker rename ${deployment.APP_NAME} ${deployment.APP_NAME}-old
  docker stop ${deployment.APP_NAME}-old
fi

44444444444

mkdir -p /home/logs/${deployment.APP_NAME}
docker run -d -p ${deployment.APP_PORT}:${deployment.APP_PORT} -v /etc/localtime:/etc/localtime -v /home/logs/${deployment.APP_NAME}:/logs -e TZ=Asia/Shanghai --name ${deployment.APP_NAME} ${IMAGE_NAME}

555555555

if [[ "$(docker ps -a | grep ${deployment.APP_NAME}-old)" != "" ]]
then
  docker rm -f ${deployment.APP_NAME}-old
fi

66666666666666