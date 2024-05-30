#!/usr/bin/env groovy

def call(Map config, Map deployment) {
    echo "进入groovy脚本方法"
    if (config.TYPE == "jar") {
        pipeline {
            agent any
            environment {
                // 默认镜像
                DEFAULT_JDK_DOCKER_IMAGE = "openjdk:8"
                // 默认jdk
                DEFAULT_JAVA_HOME = "/usr/local/java/jdk1.8.0_281"
                // 默认maven
                DEFAULT_MAVEN_HOME = "/usr/local/maven/apache-maven-3.8.1"
                // 远程服务器IP
                REMOTE_SERVER_IP = "192.168.1.79"
                // Harbor镜像仓库地址
                HARBOR_SERVER_IP = "192.168.1.98"
            }
            parameters {
                // 发布环境
                choice(name: "ENV_NAME", choices: config.ENV_NAMES, description: "发布环境")
            }
            stages {
                stage('输出配置信息') {
                    steps {
                        script {

                            env.PROFILE = config["${ENV_NAME}"].PROFILE
                            env.BRANCH = config["${ENV_NAME}"].BRANCH
                            env.JAVA_HOME = DEFAULT_JAVA_HOME
                            env.MAVEN_HOME = DEFAULT_MAVEN_HOME

                            env.DEFAULT_JDK_DOCKER_IMAGE = DEFAULT_JDK_DOCKER_IMAGE
                            if (deployment.JDK_DOCKER_IMAGE != "") {
                                DEFAULT_JDK_DOCKER_IMAGE = deployment.JDK_DOCKER_IMAGE
                            }
                            env.IMAGE_NAME = "repository/${deployment.APP_NAME}:latest"

                            echo "默认JDK镜像: ${DEFAULT_JDK_DOCKER_IMAGE}"
                            echo "应用: ${deployment.APP_NAME}"
                            echo "端口: ${deployment.APP_PORT}"
                            echo "构建类型：${config.TYPE}"
                            echo "发布环境：${PROFILE}"
                        }
                    }
                }

                stage("拉取git仓库代码") {
                    steps {
                        echo "git仓库地址: ${deployment.GIT_URL} 分支: ${BRANCH} PROFILE: ${PROFILE}"
                        git credentialsId: "ly1836_github", url: deployment.GIT_URL, branch: BRANCH
                    }
                }

                stage("编译Maven工程") {
                    steps {
                        script {
                            // https://www.jenkins.io/doc/pipeline/examples/
                            withEnv(["JAVA_HOME=${JAVA_HOME}", "PATH+MAVEN=${MAVEN_HOME}/bin:${JAVA_HOME}/bin"]) {
                                echo "=================================================="
                                sh "mvn -version"
                                echo "=================================================="
                                if (PROFILE == "") {
                                    sh "mvn clean package -T 8C -DskipTests=true -B -e -U"
                                } else {
                                    // https://www.jianshu.com/p/25aff2bf6e56
                                    sh "mvn clean package -T 8C -DskipTests=true -P${PROFILE} -B -e -U"
                                }
                            }
                        }
                    }
                }
//                stage('打包镜像') {
//                    steps {
//                        script {
//                            sh "echo 'FROM ${DEFAULT_JDK_DOCKER_IMAGE}\n" +
//                                    "VOLUME /tmp\n" +
//                                    "ADD ${deployment.FILE} ${deployment.APP_NAME}.jar\n" +
//                                    "ENTRYPOINT [\"java\",\"-Djava.security.egd=file:/dev/./urandom\",\"-jar\",\"/${deployment.APP_NAME}.jar\"]' > Dockerfile "
//                            sh "cat ./Dockerfile"
//                            sh "docker build -t ly753/${deployment.APP_NAME}:latest -f ./Dockerfile ."
//                        }
//                    }
//                }

                stage('打包上传镜像') {
                    steps {
                        script {
                            sh "echo 'FROM ${DEFAULT_JDK_DOCKER_IMAGE}\n" +
                                    "VOLUME /tmp\n" +
                                    "ADD ${deployment.FILE} ${deployment.APP_NAME}.jar\n" +
                                    "ENTRYPOINT [\"java\",\"-Djava.security.egd=file:/dev/./urandom\",\"-jar\",\"/${deployment.APP_NAME}.jar\"]' > Dockerfile "
                            sh "cat ./Dockerfile"
                            docker.withRegistry('http://${HARBOR_SERVER_IP}/repository', 'harbor_admin') {
                                def dockerImage = docker.build("${IMAGE_NAME}", "-f ./Dockerfile .")
                                dockerImage.push()
                            }
                            sh "docker rmi ${IMAGE_NAME}"
                            echo "删除镜像：${IMAGE_NAME}"
                        }
                    }
                }

                stage('远程服务器部署') {
                    steps {
                        script {
                            sshagent(credentials: ['ssh_192_168_1_79']) {
                                sh '''
                                    [ -d ~/.ssh ] || mkdir ~/.ssh && chmod 0700 ~/.ssh
                                    ssh-keyscan -t rsa,dsa ${REMOTE_SERVER_IP} >> ~/.ssh/known_hosts
                                    ssh root@${REMOTE_SERVER_IP} -o StrictHostKeyChecking=no -t \
                                        '\
                                            docker login ${HARBOR_SERVER_IP} -uadmin -pleiyang1024.; \
                                            docker pull ${HARBOR_SERVER_IP}/repository/im-user:latest; \
                                            docker run -it --name ${deployment.APP_NAME} -p ${deployment.APP_PORT}:${deployment.APP_PORT} ${HARBOR_SERVER_IP}/repository/im-user:latest; \
                                        '\
                                   '''
                            }
                        }
                    }
                }

            }

            post {
                always {
                    deleteDir()
                    echo "发布完毕： 应用: ${deployment.APP_NAME}"
                }
            }
        }
    }
}

return this;