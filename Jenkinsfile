pipeline {
    agent any

    options {
        // Pipeline 플러그인 기본 기능
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')

        // Timestamper 플러그인을 설치한 경우에만 사용
        timestamps()
    }

    parameters {
        string(
            name: 'BACKEND_BRANCH',
            defaultValue: 'develop',
            description: '백엔드 빌드 브랜치'
        )

        string(
            name: 'FRONTEND_BRANCH',
            defaultValue: 'develop',
            description: '프런트엔드 빌드 브랜치'
        )
    }

    environment {
        BACKEND_REPOSITORY =
            'https://github.com/KB-IT-s-your-life-PJT-23-4/backend.git'

        FRONTEND_REPOSITORY =
            'https://github.com/KB-IT-s-your-life-PJT-23-4/frontend.git'

        BACKEND_IMAGE = 'wosyh18/mirizoom-backend'
        FRONTEND_IMAGE = 'wosyh18/mirizoom-frontend'

    }

    stages {
        stage('Checkout') {
            parallel {
                stage('Backend Checkout') {
                    steps {
                        dir('backend') {
                            git(
                                branch: params.BACKEND_BRANCH,
                                url: env.BACKEND_REPOSITORY
                            )
                        }
                    }
                }

                stage('Frontend Checkout') {
                    steps {
                        dir('frontend') {
                            git(
                                branch: params.FRONTEND_BRANCH,
                                url: env.FRONTEND_REPOSITORY
                            )
                        }
                    }
                }
            }
        }

        stage('Validate') {
            steps {
                sh '''
                    set -eu

                    docker --version

                    test -f backend/Dockerfile
                    test -f frontend/Dockerfile
                    test -f frontend/nginx.conf

                    echo "Backend branch: ${BACKEND_BRANCH}"
                    echo "Frontend branch: ${FRONTEND_BRANCH}"
                    echo "Image tag: ${BUILD_NUMBER}"
                '''
            }
        }

        stage('Build Images') {
            parallel {
                stage('Backend Image') {
                    steps {
                        sh '''
                            set -eu

                            docker build \
                                --pull \
                                --tag "${BACKEND_IMAGE}:${BUILD_NUMBER}" \
                                --tag "${BACKEND_IMAGE}:latest" \
                                backend
                        '''
                    }
                }

                stage('Frontend Image') {
                    steps {
                        withCredentials([
                            string(
                                credentialsId: 'mirizoom-kakao-js-key',
                                variable: 'VITE_KAKAO_JS_KEY'
                            )
                        ]) {
                            sh '''
                                set -eu

                                docker build \
                                    --pull \
                                    --build-arg VITE_API_BASE_URL=/api \
                                    --build-arg VITE_KAKAO_JS_KEY="${VITE_KAKAO_JS_KEY}" \
                                    --tag "${FRONTEND_IMAGE}:${BUILD_NUMBER}" \
                                    --tag "${FRONTEND_IMAGE}:latest" \
                                    frontend
                            '''
                        }
                    }
                }
            }
        }

        stage('Push Images to Docker Hub') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'jenkins-back',
                        usernameVariable: 'DOCKERHUB_USERNAME',
                        passwordVariable: 'DOCKERHUB_TOKEN'
                    )
                ]) {
                    sh '''
                        set -eu

                        # Jenkins 서버에 Docker Hub 인증정보를 영구 저장하지 않고
                        # 현재 Workspace 안에서만 사용합니다.
                        export DOCKER_CONFIG="${WORKSPACE}/.docker"
                        mkdir -p "${DOCKER_CONFIG}"

                        echo "${DOCKERHUB_TOKEN}" |
                            docker login \
                                --username "${DOCKERHUB_USERNAME}" \
                                --password-stdin

                        echo "백엔드 이미지 Push"
                        docker push "${BACKEND_IMAGE}:${BUILD_NUMBER}"
                        docker push "${BACKEND_IMAGE}:latest"

                        echo "프런트엔드 이미지 Push"
                        docker push "${FRONTEND_IMAGE}:${BUILD_NUMBER}"
                        docker push "${FRONTEND_IMAGE}:latest"
                    '''
                }
            }
        }
        post {
                    success {
                        echo """
                        Docker Hub Push 완료

                        Backend:
                        ${BACKEND_IMAGE}:${BUILD_NUMBER}

                        Frontend:
                        ${FRONTEND_IMAGE}:${BUILD_NUMBER}
                        """
                    }

                    failure {
                        echo 'Docker 이미지 Build 또는 Push에 실패했습니다.'
                    }

                    always {
                        deleteDir()
                    }
                }
    }
}