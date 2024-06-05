#!/bin/sh

if [[ "$(docker images -q ${IMAGE_NAME} 2> /dev/null)" != "" ]];
  then
    docker rmi ${IMAGE_NAME}
fi

# docker login ${HARBOR_SERVER_IP} -u ${HARBOR_USER_NAME} -p ${HARBOR_PASSWORD};
# docker pull ${HARBOR_SERVER_IP}/${IMAGE_NAME};
docker pull ${IMAGE_NAME}

if [[ "$(docker inspect ${deployment.APP_NAME} 2> /dev/null | grep '"Name": "/${deployment.APP_NAME}"')" != "" ]];
  then
    docker rename ${deployment.APP_NAME} ${deployment.APP_NAME}_old;
    docker stop ${deployment.APP_NAME}_old;
fi

mkdir -p /home/logs/${deployment.APP_NAME};
docker run -d -p ${deployment.APP_PORT}:${deployment.APP_PORT} -v /etc/localtime:/etc/localtime -v /home/logs/${deployment.APP_NAME}:/logs -e TZ=Asia/Shanghai --name ${deployment.APP_NAME} ${IMAGE_NAME};
docker rm ${deployment.APP_NAME}_old;