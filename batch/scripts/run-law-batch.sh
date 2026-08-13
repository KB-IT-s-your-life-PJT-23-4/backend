
#!/bin/bash
#
# 주간 법령 배치 실행 래퍼. crontab 이 부르는 진입점이다.
# AWS EC2 Ubuntu 용임
#
# Ubuntu 의 /bin/sh 는 dash 라 bash 문법이 통하지 않는다. 그래서 셔뱅을 bash 로 명시
#
# cron 은 홈 디렉터리에서, 로그인 셸 환경 없이 명령을 실행(.bashrc/.profile 을 읽지 않는다).
#
#   1) 작업 디렉터리
#      batch.properties 의 law.json.dir 이 ./build/law-json 인 상대경로다. 실행 위치가 어긋나면
#      엉뚱한 곳에 JSON 을 받아 놓고 Step 2 가 "읽을 법령 JSON 이 없습니다" 로 죽는다.
#      스크립트 자기 위치를 기준으로 잡으므로 crontab 에 cd 를 쓸 필요가 없다.
#
#   2) JAVA_HOME
#      cron 의 PATH 는 /usr/bin:/bin 뿐이다. gradle 이 만든 bin/batch 는 JAVA_HOME 이 없으면
#      죽는데 원인이 로그에 잘 드러나지 않아 cron 실패의 단골이다.
#
# 배치의 종료코드를 그대로 돌려준다. cron 은 이 값으로만 성패를 판단한다.
#
#   위치: 배포된 distribution 루트(bin/ 과 같은 레벨). installDist 가 여기 넣어 준다.
#   개발 중 다른 데서 부를 때는 LAW_BATCH_HOME 으로 dist 루트를 지정한다.

set -u

APP_HOME="${LAW_BATCH_HOME:-$(cd "$(dirname "$0")" && pwd)}"
LAUNCHER="$APP_HOME/bin/batch"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

if [ ! -x "$LAUNCHER" ]; then
    log "실행 스크립트를 찾을 수 없습니다: $LAUNCHER"
    log "gradle :batch:installDist 로 배포본을 만들었는지, LAW_BATCH_HOME 이 맞는지 확인하세요."
    exit 1
fi

# apt 로 깐 OpenJDK 는 /usr/lib/jvm 아래에 있고 /usr/bin/java 가 그리로 링크된다.
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_BIN=$(command -v java) || {
        log "java 를 찾지 못했습니다. openjdk-17-jdk 설치 여부를 확인하세요."
        exit 1
    }

    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")
    export JAVA_HOME
fi

cd "$APP_HOME"

log "법령 배치 시작 (JAVA_HOME=$JAVA_HOME)"

"$LAUNCHER"
STATUS=$?

if [ $STATUS -eq 0 ]; then
    log "법령 배치 성공"
else
    log "법령 배치 실패 (종료코드 $STATUS)"
fi

exit $STATUS
