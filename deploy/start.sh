#!/bin/bash
# onGo Backend 시작

APP_NAME="ongo"
JAR_PATH="/data/ongo/jar/ongo-api.jar"
LOG_DIR="/data/ongo/log"
PID_FILE="/data/ongo/app.pid"
ENV_FILE="/data/ongo/.env"

mkdir -p "$LOG_DIR"

# 환경변수 로드
if [ -f "$ENV_FILE" ]; then
    # Remove Windows line endings (\r) which can break source
    sed -i 's/\r$//' "$ENV_FILE"
    set -a
    source "$ENV_FILE"
    set +a
else
    echo "[WARN] $ENV_FILE 파일이 존재하지 않습니다."
fi

# 필수 환경변수 확인
if [ -z "$JWT_SECRET" ]; then
    echo "[ERROR] JWT_SECRET 환경변수가 설정되지 않았습니다. $ENV_FILE 파일을 확인해주세요."
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

# setsid로 완전히 독립된 프로세스로 실행
setsid java \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    -Xms512m -Xmx2g \
    -XX:+UseG1GC \
    -jar "$JAR_PATH" \
    > "$LOG_DIR/backend.log" 2>&1 &

echo $! > "$PID_FILE"

# 시작 확인 (최대 90초 대기)
echo "서비스 시작 대기 중 (로그 모니터링)..."
MAX_RETRY=90
COUNT=0
STARTED=false

while [ $COUNT -lt $MAX_RETRY ]; do
    if grep -q "Started .* in .* seconds" "$LOG_DIR/backend.log"; then
        STARTED=true
        break
    fi

    # 프로세스가 죽었는지 체크
    if [ -f "$PID_FILE" ]; then
        if ! ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
            echo "프로세스가 비정상 종료되었습니다."
            break
        fi
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
    exit 1
fi
