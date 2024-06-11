#!/bin/sh

if [[ "$(docker images ${IMAGE_NAME})" != "" ]];
then
  docker rmi -f ${IMAGE_NAME}
fi

docker login ${HARBOR_SERVER_IP} -u ${HARBOR_USER_NAME} -p ${HARBOR_PASSWORD}
# docker pull ${HARBOR_SERVER_IP}/${IMAGE_NAME}
docker pull ${IMAGE_NAME}

