#!/usr/bin/env groovy

def call(Map config, Map deployment) {
    echo "进入groovy脚本方法"
    if (config.TYPE == "jar") {
        pipeline {
            agent any
            environment {
                // 默认镜像
                DEFAULT_JDK_DOCKER_IMAGE = "java:8"
                if(deployment.JDK_DOCKER_IMAGE != ''){
//                    DEFAULT_JDK_DOCKER_IMAGE = deployment.JDK_DOCKER_IMAGE
                }
                // 默认jdk
                DEFAULT_JAVA_HOME = "/usr/local/java/jdk1.8.0_281"
                // 默认maven
                DEFAULT_MAVEN_HOME = "/usr/local/maven/apache-maven-3.8.1"
            }
            parameters {
                // 发布环境
                choice(name: "ENV_NAME", choices: config.ENV_NAMES, description: "发布环境")
            }
            stages {
                stage('输出配置信息') {
                    steps {
                        script {
                            PROFILE = config["${ENV_NAME}"].PROFILE
                            env.BRANCH = config["${ENV_NAME}"].BRANCH
                            env.JAVA_HOME = DEFAULT_JAVA_HOME
                            env.BASE_IMAGE = DEFAULT_BASE_IMAGE
                            echo "应用: ${deployment.APP_NAME}"
                            echo "端口: ${deployment.APP_PORT}"
                            echo "构建类型：${config.TYPE}"
                            echo "发布环境：${PROFILE}"
                        }
                    }
                }
                stage("拉取git仓库代码") {
                    when {
                        environment name: 'UPDATE', value: 'false'
                    }
                    steps {
                        echo "git仓库地址: ${deployment.GIT_URL} 分支: ${BRANCH} PROFILE: ${PROFILE} NAME_SPACE: ${NAME_SPACE}"
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
                stage('打包镜像') {
                    steps {
                        script {
                            echo "JDK镜像: ${DEFAULT_JDK_DOCKER_IMAGE}"
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