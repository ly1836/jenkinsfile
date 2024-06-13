#!/usr/bin/env groovy


def call(Map config, Map deployment) {
    echo "进入groovy脚本方法"
    // docker仓库配置
    def dockerConfig = [
            // 对应三个发布环境：开发、测试、生产
            Registrys     : ["local_harbor", "docker_hub", "aliyun"],
            "local_harbor": [
                    USER_NAME: "admin",
                    PASSWORD : "admin",
                    REGISTRY : "192.168.1.98"
            ],
            "docker_hub"  : [
                    USER_NAME: "",
                    PASSWORD : "",
                    REGISTRY : ""
            ],
            "aliyun"      : [
                    USER_NAME: "18674492943",
                    PASSWORD : "ly123456",
                    REGISTRY : "registry.cn-hangzhou.aliyuncs.com"
            ]
    ]

    if (config.TYPE == "jar") {
        pipeline {
            agent any
            tools {
                'org.jenkinsci.plugins.docker.commons.tools.DockerTool' 'docker-jenkins'
            }
            environment {
                // 默认镜像
                DEFAULT_JDK_DOCKER_IMAGE = "openjdk:8"
                // 默认jdk
                //DEFAULT_JAVA_HOME = "/usr/local/java/jdk1.8.0_281"
                DEFAULT_JAVA_HOME = tool name: 'jdk-jenkins-1._8_0_281'
                // 默认maven
                //DEFAULT_MAVEN_HOME = "/usr/local/maven/apache-maven-3.8.1"
                DEFAULT_MAVEN_HOME = tool name: 'maven-jenkins-3_8_1'
                docker = tool name: 'docker-jenkins'
//                DOCKER_CERT_PATH = credentials('docker-jenkins')
                // 远程服务器IP
                REMOTE_SERVER_IP = "192.168.1.79"
            }
            parameters {
                // 发布环境
                choice(name: "ENV_NAME", choices: config.ENV_NAMES, description: "发布环境")
                // 只更新jenkins配置但是不发布
                choice(name: "REFRESH", choices: ["false", "true"], description: "是否只更新jenkins配置但是不发布")
                // 容器仓库类型
                choice(name: "REGISTRY_TYPE", choices: dockerConfig.Registrys, description: "容器仓库类型")
            }
            stages {
                stage('输出配置信息') {
                    when {
                        environment name: 'REFRESH', value: 'false'
                    }
                    steps {
                        script {
                            sh "docker version"
                            env.PROFILE = config["${ENV_NAME}"].PROFILE
                            env.BRANCH = config["${ENV_NAME}"].BRANCH

                            // docker配置
                            env.DOCKER_REGISTRY = dockerConfig["${REGISTRY_TYPE}"].REGISTRY
                            env.DOCKER_USER_NAME = dockerConfig["${REGISTRY_TYPE}"].USER_NAME
                            env.DOCKER_PASSWORD = dockerConfig["${REGISTRY_TYPE}"].PASSWORD
                            if (REGISTRY_TYPE == "local_harbor") {
                                env.IMAGE_NAME = "${DOCKER_REGISTRY}/repository/${deployment.APP_NAME}:latest"
                            } else if (REGISTRY_TYPE == "aliyun") {
                                env.IMAGE_NAME = "${DOCKER_REGISTRY}/aliyun_name_space_ly/${deployment.APP_NAME}:latest"
                            } else {
                                env.IMAGE_NAME = "ly753/${deployment.APP_NAME}:latest"
                            }

                            // 默认jdk镜像
                            env.DEFAULT_JDK_DOCKER_IMAGE = DEFAULT_JDK_DOCKER_IMAGE
                            if (deployment.JDK_DOCKER_IMAGE != "") {
                                DEFAULT_JDK_DOCKER_IMAGE = deployment.JDK_DOCKER_IMAGE
                            }

                            // 构建用户
                            env.BUILD_USER_ID = null
                            wrap([$class: 'BuildUser']) {
                                env.BUILD_USER_ID = env.BUILD_USER_ID
                            }

                            // 生产环境发布权限判断
                            env.PERMISSIONS = true
                            if(ENV_NAME == "Production"){
                                def masterUsers = readFile "./deploy/jenkins/master-user.txt"
                                def lines = masterUsers.readLines()
                                boolean exist = false
                                for (line in lines) {
                                    echo "Production权限用户：${line}"
                                    if(line == BUILD_USER_ID){
                                        exist = true;
                                    }
                                }
                                env.PERMISSIONS = exist
                            }

                            echo "默认JDK镜像: ${DEFAULT_JDK_DOCKER_IMAGE}"
                            echo "应用: ${deployment.APP_NAME}"
                            echo "端口: ${deployment.APP_PORT}"
                            echo "构建类型：${config.TYPE}"
                            echo "发布环境：${PROFILE}"
                            echo "容器仓库类型：${REGISTRY_TYPE}"
                            echo "构建者：${BUILD_USER_ID}"
                            echo "是否有发布权限：${PERMISSIONS}"
                        }
                    }
                }

                stage("拉取git仓库代码") {
                    when {
                        environment name: 'REFRESH', value: 'false'
                        environment name: 'PERMISSIONS', value: 'true'
                    }
                    steps {
                        dir('project-workspace') {
                            echo "git仓库地址: ${deployment.GIT_URL} 分支: ${BRANCH} PROFILE: ${PROFILE}"
                            git credentialsId: "ly1836_github", url: deployment.GIT_URL, branch: BRANCH
                            sh "ls -l ../"
                        }
                    }
                }

                stage("编译Maven工程") {
                    when {
                        environment name: 'REFRESH', value: 'false'
                        environment name: 'PERMISSIONS', value: 'true'
                    }
                    steps {
                        script {
                            dir('project-workspace') {
                                // https://www.jenkins.io/doc/pipeline/examples/
                                withEnv(["JAVA_HOME=${DEFAULT_JAVA_HOME}", "PATH+MAVEN=${DEFAULT_MAVEN_HOME}/bin:${JAVA_HOME}/bin"]) {
                                    echo "=================================================="
                                    sh "mvn -version"
                                    echo "=================================================="
                                    if (PROFILE == "") {
                                        sh "mvn clean package -T 8C -DskipTests=true -B -e -U -s /home/maven/settings.xml"
                                    } else {
                                        // https://www.jianshu.com/p/25aff2bf6e56
                                        sh "mvn clean package -T 8C -DskipTests=true -P${PROFILE} -B -e -U -s /home/maven/settings.xml"
                                    }
                                }
                            }
                        }
                    }
                }

                stage('打包上传镜像') {
                    when {
                        environment name: 'REFRESH', value: 'false'
                        environment name: 'PERMISSIONS', value: 'true'
                    }
                    steps {
                        script {
                            sh "sed -i 's#\${DEFAULT_JDK_DOCKER_IMAGE}#${DEFAULT_JDK_DOCKER_IMAGE}#g' ./deploy/docker/jar/Dockerfile"
                            sh "sed -i 's#\${deployment.FILE}#${deployment.FILE}#g' ./deploy/docker/jar/Dockerfile"
                            sh "sed -i 's#\${deployment.APP_NAME}#${deployment.APP_NAME}#g' ./deploy/docker/jar/Dockerfile"
                            sh "cat ./deploy/docker/jar/Dockerfile"
                            if (REGISTRY_TYPE == "local_harbor") {
                                docker.withRegistry("http://${DOCKER_REGISTRY}", 'harbor_admin') {
                                    def dockerImage = docker.build("${IMAGE_NAME}", "-f ./deploy/docker/jar/Dockerfile ./project-workspace")
                                    dockerImage.push()
                                }
                            } else if (REGISTRY_TYPE == "aliyun") {
                                docker.withRegistry("http://${DOCKER_REGISTRY}", 'aliyun_docker_18674492943') {
                                    def dockerImage = docker.build("${IMAGE_NAME}", "-f ./deploy/docker/jar/Dockerfile ./project-workspace")
                                    dockerImage.push()
                                }
                            } else {
                                docker.withRegistry("", 'dockerhub_ly753') {
                                    def dockerImage = docker.build("${IMAGE_NAME}", "-f ./deploy/docker/jar/Dockerfile ./project-workspace")
                                    dockerImage.push()
                                }
                            }
                            sh "docker rmi -f ${IMAGE_NAME}"
                            echo "删除镜像：${IMAGE_NAME}"
                        }
                    }
                }

                stage('远程服务器部署') {
                    when {
                        environment name: 'REFRESH', value: 'false'
                        environment name: 'PERMISSIONS', value: 'true'
                    }
                    steps {
                        script {
                            sh "sed -i 's#\${DOCKER_REGISTRY}#${DOCKER_REGISTRY}#g' ./deploy/sh/deploy.sh"
                            sh "sed -i 's#\${DOCKER_USER_NAME}#${DOCKER_USER_NAME}#g' ./deploy/sh/deploy.sh"
                            sh "sed -i 's#\${DOCKER_PASSWORD}#${DOCKER_PASSWORD}#g' ./deploy/sh/deploy.sh"
                            sh "sed -i 's#\${IMAGE_NAME}#${IMAGE_NAME}#g' ./deploy/sh/deploy.sh"
                            sh "sed -i 's#\${deployment.APP_NAME}#${deployment.APP_NAME}#g' ./deploy/sh/deploy.sh"
                            sh "sed -i 's#\${deployment.APP_PORT}#${deployment.APP_PORT}#g' ./deploy/sh/deploy.sh"
                            String deploy_sh = readFile './deploy/sh/deploy.sh'
                            echo "============远程服务器部署脚本==================="
                            echo deploy_sh
                            echo "============远程服务器部署脚本==================="

                            sshagent(credentials: ['ssh_192_168_1_79']) {
                                sh """
                                    [ -d ~/.ssh ] || mkdir ~/.ssh && chmod 0700 ~/.ssh
                                    ssh-keyscan -t rsa,dsa ${REMOTE_SERVER_IP} >> ~/.ssh/known_hosts
                                    scp ./deploy/sh/deploy.sh root@${REMOTE_SERVER_IP}:~/
                                    ssh root@${REMOTE_SERVER_IP} -o StrictHostKeyChecking=no -t \
                                        '\
                                            chmod +x ./deploy.sh; \
                                            ./deploy.sh ${REGISTRY_TYPE} ; \
                                            rm -f ./deploy.sh; \
                                        '\
                                   """
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