#!/usr/bin/env bash
# setup-server.sh 의 systemd 설치 계약을 정적으로 검증한다.
# 실제 root 권한·패키지 업데이트·systemd 변경은 실행하지 않는다.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/oracle/setup-server.sh"
UNIT="$SCRIPT_DIR/ongo-backend.service"
PASS=0
FAIL=0

ok() { printf '  ok   %s\n' "$1"; PASS=$((PASS + 1)); }
bad() { printf '  FAIL %s\n     %s\n' "$1" "$2"; FAIL=$((FAIL + 1)); }

if [ ! -f "$TARGET" ]; then
    echo "대상 스크립트가 없습니다: $TARGET" >&2
    exit 1
fi

if bash -n "$TARGET" 2>/dev/null; then
    ok "setup-server.sh bash 문법"
else
    bad "setup-server.sh bash 문법" "bash -n 실패"
fi

if grep -qE '^SYSTEMD_UNIT_SOURCE=.*ongo-backend\.service' "$TARGET"; then
    ok "저장소의 backend 유닛을 소스로 사용한다"
else
    bad "저장소의 backend 유닛을 소스로 사용한다" "SYSTEMD_UNIT_SOURCE가 없습니다."
fi

if grep -qE 'install -o root -g root -m 0644 "\$SYSTEMD_UNIT_SOURCE" "\$SYSTEMD_UNIT_TARGET"' "$TARGET"; then
    ok "유닛을 root 소유·0644로 설치한다"
else
    bad "유닛을 root 소유·0644로 설치한다" "install 계약이 없습니다."
fi

if grep -qE 'systemctl daemon-reload' "$TARGET" && grep -qE 'systemctl enable ongo-backend\.service' "$TARGET"; then
    ok "설치 후 daemon-reload와 enable을 수행한다"
else
    bad "설치 후 daemon-reload와 enable을 수행한다" "systemd 갱신/부팅 자동 시작 계약이 없습니다."
fi

if grep -qE "getent passwd jenkins" "$TARGET"; then
    ok "유닛 실행 주체(jenkins)를 설치 전에 확인한다"
else
    bad "유닛 실행 주체(jenkins)를 설치 전에 확인한다" "jenkins 사용자 검증이 없습니다."
fi

if grep -qE 'systemctl (start|enable --now) ongo-backend' "$TARGET"; then
    bad "초기 설정에서 backend를 자동 기동하지 않는다" "자격증명 검증 전 systemctl start/enable --now가 있습니다."
else
    ok "초기 설정에서 backend를 자동 기동하지 않는다"
fi

if [ -f "$UNIT" ] && grep -qE '^ExecStart=.*deploy/start\.sh' "$UNIT"; then
    ok "설치 대상 유닛이 start.sh 계약을 유지한다"
else
    bad "설치 대상 유닛이 start.sh 계약을 유지한다" "ongo-backend.service의 ExecStart를 확인할 수 없습니다."
fi

if grep -qE '^Environment=ONGO_SYSTEMD_MANAGED=true' "$UNIT"; then
    ok "systemd 기동이 기존 수동 JVM 전환 모드를 켠다"
else
    bad "systemd 기동이 기존 수동 JVM 전환 모드를 켠다" "ONGO_SYSTEMD_MANAGED 환경변수가 없습니다."
fi

echo
printf '결과: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
