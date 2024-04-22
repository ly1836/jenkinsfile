#!/usr/bin/env groovy

def call(Map config, Map deployment) {
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
            post {
                always {
                    deleteDir()
                    echo "发布完毕： 应用: ${deployment.APP_NAME}"
                }
            }
        }
    }
}
