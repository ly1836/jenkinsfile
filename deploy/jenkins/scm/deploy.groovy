#!/usr/bin/env groovy

def call(Map config, Map deployment) {
    echo "进入groovy脚本方法"
    if (config.TYPE == "jar") {
        pipeline {
            agent any
            environment {
                // 默认jdk
                DEFAULT_JAVA_HOME = "/home/java/jdk-11"
            }
            parameters {
                // 发布环境
                choice(name: "ENV_NAME", choices: config.ENV_NAMES, description: "发布环境")
            }
            stages {
                stage('build-stage-1') {
                    steps {
                        script {
                            echo "开始发布： 应用: ${deployment.APP_NAME}"
                            echo "构建类型：${config.TYPE}"
                            echo "发布环境：${config.ENV_NAME}"
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