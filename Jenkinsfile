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

        BACKEND_IMAGE = 'mirizoom-backend'
        FRONTEND_IMAGE = 'mirizoom-frontend'

        BACKEND_CONTAINER = 'backend'
        FRONTEND_CONTAINER = 'mirizoom-frontend'

        DOCKER_NETWORK = 'mirizoom-network'
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

        stage('Create Docker Network') {
            steps {
                sh '''
                    if ! docker network inspect "${DOCKER_NETWORK}" \
                        >/dev/null 2>&1
                    then
                        docker network create "${DOCKER_NETWORK}"
                    fi
                '''
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'mirizoom-backend-env',
                        variable: 'BACKEND_ENV_FILE'
                    )
                ]) {
                    sh '''
                        set -eu

                        # Nginx가 기존 backend 컨테이너 IP를 기억할 수 있으므로
                        # 프런트 컨테이너부터 중지합니다.
                        docker rm -f "${FRONTEND_CONTAINER}" \
                            >/dev/null 2>&1 || true

                        docker rm -f "${BACKEND_CONTAINER}" \
                            >/dev/null 2>&1 || true

                        docker run -d \
                            --name "${BACKEND_CONTAINER}" \
                            --restart unless-stopped \
                            --network "${DOCKER_NETWORK}" \
                            --network-alias backend \
                            --env-file "${BACKEND_ENV_FILE}" \
                            "${BACKEND_IMAGE}:${BUILD_NUMBER}"

                        docker run -d \
                            --name "${FRONTEND_CONTAINER}" \
                            --restart unless-stopped \
                            --network "${DOCKER_NETWORK}" \
                            --publish 80:80 \
                            "${FRONTEND_IMAGE}:${BUILD_NUMBER}"
                    '''
                }
            }
        }

        stage('Verify') {
            steps {
                sh '''
                    set -eu

                    echo "컨테이너 실행 상태"
                    docker ps \
                        --filter "name=${BACKEND_CONTAINER}" \
                        --filter "name=${FRONTEND_CONTAINER}"

                    echo "프런트엔드 응답 확인"

                    SUCCESS=false

                    for COUNT in $(seq 1 20)
                    do
                        if curl --fail --silent \
                            --show-error \
                            http://127.0.0.1/ \
                            >/dev/null
                        then
                            SUCCESS=true
                            break
                        fi

                        echo "서비스 시작 대기: ${COUNT}/20"
                        sleep 3
                    done

                    if [ "${SUCCESS}" != "true" ]
                    then
                        echo "프런트엔드 응답 확인 실패"

                        docker logs \
                            --tail 100 \
                            "${FRONTEND_CONTAINER}" || true

                        docker logs \
                            --tail 100 \
                            "${BACKEND_CONTAINER}" || true

                        exit 1
                    fi
                '''
            }
        }
    }

    post {
        success {
            echo "MiriZoom 배포가 완료되었습니다."
        }

        failure {
            echo "파이프라인이 실패했습니다."

            sh '''
                docker ps -a \
                    --filter "name=${BACKEND_CONTAINER}" \
                    --filter "name=${FRONTEND_CONTAINER}" \
                    || true

                docker logs \
                    --tail 100 \
                    "${BACKEND_CONTAINER}" || true

                docker logs \
                    --tail 100 \
                    "${FRONTEND_CONTAINER}" || true
            '''
        }

        always {
            deleteDir()
        }
    }
}