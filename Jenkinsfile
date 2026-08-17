pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    environment {
        IMAGE_NAME = 'wosyh18/mirizoom-backend'
        REPOSITORY_URL = 'https://github.com/KB-IT-s-your-life-PJT-23-4/backend.git'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Validate') {
            steps {
                sh '''
                    set -eu

                    docker --version
                    test -f Dockerfile
                    test -f gradlew
                    test -f settings.gradle

                    echo "Repository: ${REPOSITORY_URL}"
                    echo "Commit: $(git rev-parse HEAD)"
                    echo "Image: ${IMAGE_NAME}:${BUILD_NUMBER}"
                '''
            }
        }

        stage('Test') {
            steps {
                sh '''
                    set -eu

                    chmod +x gradlew
                    ./gradlew test --no-daemon
                '''
            }
        }

        stage('Build Image') {
            steps {
                sh '''
                    set -eu

                    docker build \
                        --pull \
                        --tag "${IMAGE_NAME}:${BUILD_NUMBER}" \
                        --tag "${IMAGE_NAME}:latest" \
                        .
                '''
            }
        }

        stage('Push Image to Docker Hub') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKERHUB_USERNAME',
                        passwordVariable: 'DOCKERHUB_TOKEN'
                    )
                ]) {
                    sh '''
                        set -eu

                        export DOCKER_CONFIG="${WORKSPACE}/.docker"
                        mkdir -p "${DOCKER_CONFIG}"

                        echo "${DOCKERHUB_TOKEN}" |
                            docker login \
                                --username "${DOCKERHUB_USERNAME}" \
                                --password-stdin

                        docker push "${IMAGE_NAME}:${BUILD_NUMBER}"
                        docker push "${IMAGE_NAME}:latest"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Backend 이미지 Push 완료: ${IMAGE_NAME}:${BUILD_NUMBER}"
        }

        failure {
            echo 'Backend 테스트, 이미지 Build 또는 Push에 실패했습니다.'
        }

        always {
            deleteDir()
        }
    }
}
