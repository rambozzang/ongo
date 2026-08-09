#!/bin/bash
# onGo Backend 중지

APP_NAME="ongo"
PID_FILE="/data/ongo/app.pid"
JAR_NAME="ongo-api.jar"

is_ongo_process() {
    local pid="$1"
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    ps -p "$pid" -o command= 2>/dev/null | grep -Fq -- "$JAR_NAME"
}

stop_pid() {
    local pid="$1"
    if ! is_ongo_process "$pid"; then
        echo "PID $pid 는 $JAR_NAME 프로세스가 아니므로 종료하지 않습니다."
        return 1
    fi

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $APP_NAME (PID: $pid) 중지 중..."
    kill "$pid"

    for i in {1..30}; do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            echo "$APP_NAME 중지 완료."
            return 0
        fi
        sleep 1
    done

    echo "강제 종료(SIGKILL)..."
    if is_ongo_process "$pid"; then
        kill -9 "$pid"
    else
        echo "PID $pid 의 명령이 바뀌어 강제 종료하지 않습니다."
        return 1
    fi
    return 0
}

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if is_ongo_process "$PID"; then
        stop_pid "$PID" || true
        rm -f "$PID_FILE"
    else
        echo "PID 파일의 프로세스가 없거나 다른 명령입니다. 종료하지 않고 PID 파일만 정리합니다."
        rm -f "$PID_FILE"
    fi
else
    # PID 파일이 없는 경우, 명령행에 JAR가 있는 프로세스만 확인한다.
    PID=$(pgrep -f -- "$JAR_NAME" | head -1 || true)
    if [ -n "$PID" ]; then
        stop_pid "$PID"
    else
        echo "$APP_NAME이 실행 중이 아닙니다."
    fi
fi
