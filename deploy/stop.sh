#!/bin/bash
# onGo Backend 중지

APP_NAME="ongo"
PID_FILE="/data/ongo/app.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] $APP_NAME (PID: $PID) 중지 중..."
        kill $PID

        for i in {1..30}; do
            if ! ps -p $PID > /dev/null 2>&1; then
                echo "$APP_NAME 중지 완료."
                rm -f "$PID_FILE"
                exit 0
            fi
            sleep 1
        done

        echo "강제 종료(SIGKILL)..."
        kill -9 $PID
        rm -f "$PID_FILE"
    else
        echo "프로세스가 이미 종료됨. PID 파일 정리."
        rm -f "$PID_FILE"
    fi
else
    echo "$APP_NAME이 실행 중이 아닙니다."
fi
