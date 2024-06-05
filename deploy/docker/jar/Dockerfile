FROM ${DEFAULT_JDK_DOCKER_IMAGE}
VOLUME /tmp
ADD ${deployment.FILE} ${deployment.APP_NAME}.jar
ENTRYPOINT [java, -Djava.security.egd=file:/dev/./urandom, -jar, /${deployment.APP_NAME}.jar]