#!/bin/sh

# 1.重命名容器、停止容器
if [[ "$(docker ps -a | grep ${deployment.APP_NAME})" != "" ]]
then
  docker rename ${deployment.APP_NAME} ${deployment.APP_NAME}-old
  docker stop ${deployment.APP_NAME}-old
fi

# 2.删除镜像
if [[ "$(docker images ${IMAGE_NAME})" != "" ]]
then
  docker rmi -f ${IMAGE_NAME}
fi

# 3.拉取镜像
if [[ $1 == "local_harbor" ]] || [[ $1 == "aliyun" ]]
then
  docker login ${DOCKER_REGISTRY} -u ${DOCKER_USER_NAME} -p ${DOCKER_PASSWORD}
fi
docker pull ${IMAGE_NAME}

# 4.启动容器
mkdir -p /home/logs/${deployment.APP_NAME}
docker run -d -p ${deployment.APP_PORT}:${deployment.APP_PORT} -v /etc/localtime:/etc/localtime -v /home/logs/${deployment.APP_NAME}:/logs -e TZ=Asia/Shanghai --name ${deployment.APP_NAME} ${IMAGE_NAME}

# 5.删除旧容器
if [[ "$(docker ps -a | grep ${deployment.APP_NAME}-old)" != "" ]]
then
  docker rm -f ${deployment.APP_NAME}-old
fi
