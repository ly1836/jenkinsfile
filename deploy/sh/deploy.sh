#!/bin/sh

if [[ "$(docker images ${IMAGE_NAME})" != "" ]];
then
  docker rmi -f ${IMAGE_NAME}
fi
