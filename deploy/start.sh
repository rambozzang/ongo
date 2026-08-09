#!/bin/bash
# onGo Backend 시작

APP_NAME="ongo"
JAR_PATH="/data/ongo/jar/ongo-api.jar"
LOG_DIR="/data/ongo/log"
PID_FILE="/data/ongo/app.pid"
ENV_FILE="/data/ongo/.env"

mkdir -p "$LOG_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 필수 변수 목록과 검사 함수는 deploy.sh 와 공유한다. 목록이 갈라지면
# 선행 검증을 통과하고 여기서 죽어 서비스가 멈춘 채 다시 뜨지 않는다.
# shellcheck source=deploy/required-env.sh
source "$SCRIPT_DIR/required-env.sh"

# 환경변수 로드.
# 파일이 없는 것은 기존 동작대로 경고만 한다(어차피 아래 필수 변수 검증에서 걸린다).
# 다만 파일이 있는데 읽지 못하는 경우는 조용히 넘기지 않는다.
ongo_load_env_file "$ENV_FILE"
case $? in
    0) ;;
    1) echo "[WARN] $ENV_FILE 파일이 존재하지 않습니다." ;;
    *) echo "[ERROR] $ENV_FILE 을 읽을 수 없습니다(문법 오류). 파일을 확인해주세요."
       exit 1 ;;
esac

# 필수 환경변수 검증.
# 정상 배포 경로에서는 deploy.sh 가 서비스를 멈추기 전에 이미 같은 검사를 통과했다.
# 여기 검증은 수동 기동 등 다른 경로를 위한 방어선이다.
MISSING_VARS="$(ongo_missing_env_vars)"
if [ -n "$MISSING_VARS" ]; then
    ongo_report_missing_env_vars "$MISSING_VARS" "$ENV_FILE"
    exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] 실행할 JAR 파일이 없습니다: $JAR_PATH"
    exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] $APP_NAME 시작..."

# 이미 실행 중인지 확인
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "$APP_NAME (PID: $PID)이 이미 실행 중입니다."
        exit 0
    fi
    rm -f "$PID_FILE"
fi

# 이전 기동의 성공 문구를 새 기동 성공으로 오인하지 않도록 로그를 비운다.
: > "$LOG_DIR/backend.log"

# setsid로 완전히 독립된 프로세스로 실행
setsid java \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    -Xms512m -Xmx2g \
    -XX:+UseG1GC \
    -XX:+CrashOnOutOfMemoryError \
    -Djava.awt.headless=true \
    -jar "$JAR_PATH" \
    > "$LOG_DIR/backend.log" 2>&1 &

echo $! > "$PID_FILE"

# 시작 확인 (최대 90초 대기)
echo "서비스 시작 대기 중 (로그 모니터링)..."
MAX_RETRY=90
COUNT=0
STARTED=false
SERVER_PORT="${SERVER_PORT:-8070}"

process_alive() {
    local pid
    pid=$(cat "$PID_FILE" 2>/dev/null || true)
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    ps -p "$pid" -o command= 2>/dev/null | grep -q 'ongo-api.jar'
}

health_ready() {
    command -v curl >/dev/null 2>&1 || return 1
    curl -fsS --max-time 2 "http://127.0.0.1:${SERVER_PORT}/actuator/health" 2>/dev/null \
        | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

while [ $COUNT -lt $MAX_RETRY ]; do
    if process_alive && { grep -q "Started .* in .* seconds" "$LOG_DIR/backend.log" || health_ready; }; then
        STARTED=true
        break
    fi

    # 프로세스가 죽었는지 체크
    if ! process_alive; then
        echo "프로세스가 비정상 종료되었습니다."
        break
    fi

    sleep 1
    COUNT=$((COUNT + 1))
    if [ $((COUNT % 10)) -eq 0 ]; then
        echo "... 대기 중 ($COUNT/$MAX_RETRY)"
    fi
done

if [ "$STARTED" = true ]; then
    echo "$APP_NAME 시작 완료 (PID: $(cat $PID_FILE))"
else
    echo "$APP_NAME 시작 실패 또는 타임아웃! 최신 로그:"
    tail -n 50 "$LOG_DIR/backend.log"
    rm -f "$PID_FILE"
    exit 1
fi
