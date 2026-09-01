#!/usr/bin/env bash
#
# deploy.sh 의 **정적 계약** 테스트.
#
# ## 무엇을 재는가
#
# 배포 스크립트는 실제로 돌려 보기 어렵다 — 운영 경로(/data/ongo)와 실행 중인 서비스를
# 건드리기 때문이다. 그래서 여기서는 스크립트를 **실행하지 않고** 텍스트로 세 가지 순서·형태만
# 고정한다. 셋 다 어겼을 때 운영에서 실제로 사고가 났던 항목이다.
#
#   1. 최종 JAR 을 in-place `cp` 하지 않는다 (임시 파일 + `mv` 로 원자 교체)
#   2. 백업 정리가 `stop.sh` 보다 앞에 있다
#   3. 필수 env 선행 검증이 JAR 발행보다 앞에 있다
#
# ## 무엇을 재지 않는가
#
# 스크립트가 실제로 배포에 성공하는지는 **증명하지 않는다.** 그건 서버와 실행 중 서비스가
# 필요하다. 여기서 잡는 것은 "순서를 되돌리는 편집" 뿐이며, 그 편집은 리뷰에서 눈에 잘
# 띄지 않으면서 결과는 조용히 치명적이다.
#
# 실행: bash deploy/deploy.test.sh   (종료 코드 0 이면 통과)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/deploy.sh"

PASS=0
FAIL=0

ok()   { printf '  ok   %s\n' "$1"; PASS=$((PASS + 1)); }
bad()  { printf '  FAIL %s\n     %s\n' "$1" "$2"; FAIL=$((FAIL + 1)); }

if [ ! -f "$TARGET" ]; then
    echo "대상 스크립트가 없습니다: $TARGET" >&2
    exit 1
fi

# deploy_backend 함수 본문만 본다. 다른 함수(프론트 배포 등)의 cp 를 오인하지 않기 위해서다.
BACKEND=$(awk '/^deploy_backend\(\)/,/^}/' "$TARGET")

# 주석에 같은 문구가 나오므로 실행문만 남긴다. 순서 판정이 설명글에 흔들리면 안 된다.
BACKEND_CODE=$(printf '%s\n' "$BACKEND" | sed 's/#.*//')

# 첫 등장 줄 번호. 없으면 빈 문자열.
line_of() { printf '%s\n' "$BACKEND_CODE" | grep -nE "$1" | head -1 | cut -d: -f1; }

# ---------------------------------------------------------------------------
echo "deploy.sh 계약"

# (a) 최종 JAR 직접 cp 금지 + 임시 파일/mv 존재
#
# in-place cp 는 같은 inode 를 truncate 한다. 실행 중 JVM 이 그 파일을 열어 둔 채라
# 지연 로딩되는 클래스가 다른 빌드의 바이트를 읽게 된다. 운영에서 8/12 기동 프로세스가
# 세 번 덮어써진 JAR 을 연 채 돌고 있었다.
if printf '%s\n' "$BACKEND_CODE" | grep -qE 'cp[[:space:]]+"\$JAR_FILE"[[:space:]]+"\$JAR_DIR/ongo-api\.jar"'; then
    bad "최종 JAR 을 직접 cp 하지 않는다" "cp \"\$JAR_FILE\" \"\$JAR_DIR/ongo-api.jar\" 가 남아 있습니다. 실행 중 JVM 이 연 파일을 덮어씁니다."
else
    ok "최종 JAR 을 직접 cp 하지 않는다"
fi

if printf '%s\n' "$BACKEND_CODE" | grep -qE 'JAR_TMP='; then
    ok "임시 파일을 거쳐 쓴다"
else
    bad "임시 파일을 거쳐 쓴다" "JAR_TMP 가 없습니다."
fi

# mv 는 같은 디렉터리여야 rename(2) 한 번으로 끝난다. 다른 파일시스템이면 복사가 되어
# 원자성이 사라진다.
if printf '%s\n' "$BACKEND_CODE" | grep -qE 'JAR_TMP="\$JAR_DIR/'; then
    ok "임시 파일이 JAR_DIR 안에 있다(같은 파일시스템)"
else
    bad "임시 파일이 JAR_DIR 안에 있다(같은 파일시스템)" "JAR_TMP 가 \$JAR_DIR 밖이면 mv 가 복사가 되어 원자적이지 않습니다."
fi

if printf '%s\n' "$BACKEND_CODE" | grep -qE 'mv[[:space:]]+-f[[:space:]]+"\$JAR_TMP"[[:space:]]+"\$JAR_DIR/ongo-api\.jar"'; then
    ok "mv -f 로 원자 교체한다"
else
    bad "mv -f 로 원자 교체한다" "mv -f \"\$JAR_TMP\" \"\$JAR_DIR/ongo-api.jar\" 가 없습니다."
fi

# 실패·중단 시 임시 파일을 남기지 않는다. 200MB 짜리가 쌓이면 디스크가 찬다.
if printf '%s\n' "$BACKEND_CODE" | grep -qE 'rm -f "\$JAR_TMP"'; then
    ok "실패 시 임시 파일을 정리한다"
else
    bad "실패 시 임시 파일을 정리한다" "rm -f \"\$JAR_TMP\" 가 없습니다."
fi

# mv 는 원본 모드를 그대로 옮긴다. 맞춰 주지 않으면 서비스 계정이 새 JAR 을 못 읽을 수 있다.
if printf '%s\n' "$BACKEND_CODE" | grep -qE 'chmod .*"\$JAR_TMP"'; then
    ok "교체 전에 읽기 권한을 맞춘다"
else
    bad "교체 전에 읽기 권한을 맞춘다" "JAR_TMP 에 chmod 가 없습니다."
fi

# (b) 백업 정리가 stop.sh 보다 앞
#
# 예전에는 기동 확인 뒤에 있었다. 그 앞의 exit 들 때문에 한 번도 도달하지 못해 운영에
# 3개만 남아야 할 백업이 6개(각 200MB) 쌓였다.
PRUNE_LINE=$(line_of 'ongo-api\.jar\.backup\.\* 2>/dev/null')
# **재시작 경계**를 찾는다. 어떤 방식으로 재시작하든(스크립트 직접 호출이든
# restart_backend 위임이든) 그 앞에 정리·마이그레이션·점검이 끝나 있어야 한다는 것이
# 이 계약의 요지다. 특정 구현에 앵커를 걸면 리팩터링에 헛돈다.
STOP_LINE=$(line_of '^[[:space:]]*restart_backend[[:space:]]*$|deploy/stop\.sh')
if [ -z "$PRUNE_LINE" ]; then
    bad "백업 정리가 재시작보다 앞이다" "백업 정리 구문을 찾지 못했습니다."
elif [ -z "$STOP_LINE" ]; then
    bad "백업 정리가 재시작보다 앞이다" "재시작 지점을 찾지 못했습니다."
elif [ "$PRUNE_LINE" -lt "$STOP_LINE" ]; then
    ok "백업 정리가 재시작보다 앞이다"
else
    bad "백업 정리가 재시작보다 앞이다" "정리(L$PRUNE_LINE)가 재시작(L$STOP_LINE) 뒤에 있어 재시작이 실패하면 실행되지 않습니다."
fi

# (c) 필수 env 선행 검증이 JAR 발행보다 앞
#
# 검증이 JAR 교체 뒤에 있으면, 검증 실패 시 새 JAR 만 자리에 놓이고 서비스는 옛 프로세스로
# 남는다. 운영에서 8/16·8/24·8/27 세 번 정확히 그렇게 됐다.
PREFLIGHT_LINE=$(line_of '^[[:space:]]*preflight_env[[:space:]]*$')
PUBLISH_LINE=$(line_of 'mv[[:space:]]+-f[[:space:]]+"\$JAR_TMP"')
BACKUP_LINE=$(line_of 'cp "\$JAR_DIR/ongo-api\.jar" "\$JAR_DIR/ongo-api\.jar\.backup')
if [ -z "$PREFLIGHT_LINE" ]; then
    bad "preflight_env 가 JAR 발행보다 앞이다" "preflight_env 호출을 찾지 못했습니다."
elif [ -z "$PUBLISH_LINE" ]; then
    bad "preflight_env 가 JAR 발행보다 앞이다" "JAR 발행(mv) 구문을 찾지 못했습니다."
elif [ "$PREFLIGHT_LINE" -lt "$PUBLISH_LINE" ]; then
    ok "preflight_env 가 JAR 발행보다 앞이다"
else
    bad "preflight_env 가 JAR 발행보다 앞이다" "preflight(L$PREFLIGHT_LINE)가 발행(L$PUBLISH_LINE) 뒤입니다."
fi

# 백업도 디스크 쓰기다. 검증 실패 시 백업조차 만들지 않아야 흔적이 남지 않는다.
if [ -n "$PREFLIGHT_LINE" ] && [ -n "$BACKUP_LINE" ] && [ "$PREFLIGHT_LINE" -lt "$BACKUP_LINE" ]; then
    ok "preflight_env 가 JAR 백업보다 앞이다"
else
    bad "preflight_env 가 JAR 백업보다 앞이다" "preflight(L${PREFLIGHT_LINE:-?})가 백업(L${BACKUP_LINE:-?})보다 뒤이거나 찾지 못했습니다."
fi

# 판매용 기본 배포는 systemd 감시자가 없는 호스트에서 JAR을 바꾸면 안 된다.
# 명시적인 ALLOW_UNMANAGED_BACKEND=true 응급 우회만 허용한다.
SUPERVISOR_LINE=$(line_of '^[[:space:]]*if[[:space:]]*!?[[:space:]]*require_managed_backend')
BUILD_LINE=$(line_of '^[[:space:]]*cd[[:space:]]+"\$SRC_DIR/backend"')
if [ -n "$SUPERVISOR_LINE" ] && [ -n "$BUILD_LINE" ] && [ "$SUPERVISOR_LINE" -lt "$BUILD_LINE" ]; then
    ok "systemd 감시자 확인이 빌드·JAR 발행보다 앞이다"
else
    bad "systemd 감시자 확인이 빌드·JAR 발행보다 앞이다" \
        "managed backend 게이트(L${SUPERVISOR_LINE:-?})가 backend 작업(L${BUILD_LINE:-?})보다 앞에 없습니다."
fi

if grep -qE 'ALLOW_UNMANAGED_BACKEND=true' "$TARGET"; then
    ok "무관리 응급 우회가 명시적 변수로만 가능하다"
else
    bad "무관리 응급 우회가 명시적 변수로만 가능하다" "ALLOW_UNMANAGED_BACKEND 안내가 없습니다."
fi

# 최종 요약도 PID 파일만으로 active를 선언하면 안 된다. systemd 상태와 HTTP health를
# 함께 확인해야 무관리 고아 JVM을 정상 배포로 오인하지 않는다.
SUMMARY_CODE=$(sed -n '/^# 배포 결과 요약/,$p' "$TARGET")
if printf '%s\n' "$SUMMARY_CODE" | grep -q 'ongo_systemd_unit_installed' \
   && printf '%s\n' "$SUMMARY_CODE" | grep -q 'backend_health_ready'; then
    ok "최종 상태 요약이 systemd와 HTTP health를 함께 확인한다"
else
    bad "최종 상태 요약이 systemd와 HTTP health를 함께 확인한다" \
        "PID/프로세스만으로 active를 표시할 수 있습니다."
fi

if printf '%s\n' "$SUMMARY_CODE" | grep -qE '\[ -f "\$PID_FILE" \].*ps -p'; then
    bad "최종 상태 요약이 PID 파일만으로 active를 선언하지 않는다" \
        "기존 PID 파일 기반 active 판정이 남아 있습니다."
else
    ok "최종 상태 요약이 PID 파일만으로 active를 선언하지 않는다"
fi

# 기존 게이트 순서를 그대로 지킨다 — 마이그레이션 → 스키마 점검 → 재시작.
MIGRATE_LINE=$(line_of 'deploy/migrate-schema\.sh')
SCHEMA_LINE=$(line_of 'deploy/preflight-schema\.sh')
if [ -n "$MIGRATE_LINE" ] && [ -n "$SCHEMA_LINE" ] && [ -n "$STOP_LINE" ] \
   && [ "$MIGRATE_LINE" -lt "$SCHEMA_LINE" ] && [ "$SCHEMA_LINE" -lt "$STOP_LINE" ]; then
    ok "마이그레이션 → 스키마 점검 → 재시작 순서가 유지된다"
else
    bad "마이그레이션 → 스키마 점검 → 재시작 순서가 유지된다" \
        "migrate(L${MIGRATE_LINE:-?}) < schema(L${SCHEMA_LINE:-?}) < restart(L${STOP_LINE:-?}) 이 아닙니다."
fi

# 재시작 뒤 PID 파일만 확인하면 오래된 JVM이나 즉시 죽은 프로세스를 성공으로 오인한다.
# 실제 애플리케이션 health 게이트가 재시작 뒤에 있어야 배포 완료를 선언할 수 있다.
HEALTH_GATE_LINE=$(line_of '^[[:space:]]*(if[[:space:]]+)?wait_for_backend_health')
if [ -n "$STOP_LINE" ] && [ -n "$HEALTH_GATE_LINE" ] && [ "$STOP_LINE" -lt "$HEALTH_GATE_LINE" ]; then
    ok "재시작 뒤 실제 JVM·HTTP health를 확인한다"
else
    bad "재시작 뒤 실제 JVM·HTTP health를 확인한다" \
        "restart(L${STOP_LINE:-?}) 뒤 wait_for_backend_health(L${HEALTH_GATE_LINE:-?}) 게이트가 없습니다."
fi

if grep -qE 'curl .*127\.0\.0\.1:\$\{SERVER_PORT\}/actuator/health' "$TARGET" \
   && grep -qE 'grep -Fq -- .*\$JAR_DIR/ongo-api\.jar' "$TARGET"; then
    ok "health 게이트가 onGo JAR 프로세스와 actuator를 함께 검증한다"
else
    bad "health 게이트가 onGo JAR 프로세스와 actuator를 함께 검증한다" \
        "프로세스 명령줄과 actuator health를 모두 확인하는 계약이 없습니다."
fi

PREVIOUS_PID_LINE=$(line_of 'PREVIOUS_BACKEND_PID=')
CURRENT_PID_LINE=$(line_of 'CURRENT_BACKEND_PID=')
if [ -n "$PREVIOUS_PID_LINE" ] && [ -n "$CURRENT_PID_LINE" ] \
   && [ "$PREVIOUS_PID_LINE" -lt "$STOP_LINE" ] \
   && [ "$STOP_LINE" -lt "$CURRENT_PID_LINE" ]; then
    ok "재시작 전후 PID가 달라야 새 JAR 실행을 인정한다"
else
    bad "재시작 전후 PID가 달라야 새 JAR 실행을 인정한다" \
        "기존 PID(L${PREVIOUS_PID_LINE:-?})와 현재 PID(L${CURRENT_PID_LINE:-?}) 비교가 재시작 경계에 없습니다."
fi

# (d) systemd 분기
#
# grep 이 아니라 **함수를 떼어내 스텁과 함께 실행한다.** 문자열만 보면 "분기가 존재한다"
# 까지밖에 못 재는데, 정작 중요한 것은 유닛이 있을 때 start.sh 를 **부르지 않는가** 이다.
#
# 유닛을 설치해 놓고 배포가 start.sh 를 직접 부르면 그 프로세스는 유닛의 cgroup 밖에서
# 뜬다. systemd 는 서비스가 멈춘 줄 알고 감시하지 않는다 — 감시자를 붙였는데 배포한
# 프로세스만 감시 밖에 남는, 안 붙인 것보다 나쁜 상태다.
RESTART_FNS=$(awk '/^ONGO_SYSTEMD_UNIT=/,/^}/' "$TARGET"; awk '/^ongo_sudo\(\)/,/^}/' "$TARGET"; awk '/^require_managed_backend\(\)/,/^}/' "$TARGET"; awk '/^restart_backend\(\)/,/^}/' "$TARGET")

# 유닛 설치 여부를 흉내 내고, 무엇이 호출됐는지 한 줄씩 기록한다.
# 실제 systemctl·stop.sh·start.sh 는 절대 실행하지 않는다.
run_restart() {
    local unit_installed="$1"
    UNIT_INSTALLED="$unit_installed" bash -c '
        set -uo pipefail
        SRC_DIR=/tmp/ongo-src-stub
        info() { :; }
        # 설치 여부를 흉내 내는 systemctl 스텁.
        systemctl() {
            if [ "$1" = "list-unit-files" ]; then
                [ "$UNIT_INSTALLED" = "yes" ] && printf "%s enabled enabled\n" "$2"
                return 0
            fi
            echo "CALL systemctl $*"
        }
        command() {
            if [ "${2:-}" = "systemctl" ] || [ "${2:-}" = "sudo" ]; then return 0; fi
            builtin command "$@"
        }
        sudo() { echo "CALL sudo $*"; }
        id() { echo 1000; }
        bash() { echo "CALL bash $*"; }
        if [ "$UNIT_INSTALLED" = "no" ]; then
            export ALLOW_UNMANAGED_BACKEND=true
        fi
        '"$RESTART_FNS"'
        restart_backend
    ' 2>&1
}

OUT_NO_UNIT=$(run_restart no)
OUT_WITH_UNIT=$(run_restart yes)

# 기본 게이트는 우회 변수가 없으면 실제로 실패해야 한다. 이 검사는 기존 fallback을
# 호출하는 restart_backend 테스트와 분리해, 배포 전 차단 지점 자체를 실행한다.
run_supervisor_gate() {
    local unit_installed="$1"
    local allow_unmanaged="$2"
    UNIT_INSTALLED="$unit_installed" ALLOW_UNMANAGED_BACKEND="$allow_unmanaged" bash -c '
        set -uo pipefail
        ONGO_SYSTEMD_UNIT=ongo-backend.service
        info() { :; }
        warn() { echo "WARN $*"; }
        error() { echo "ERROR $*"; }
        systemctl() {
            if [ "$1" = "list-unit-files" ]; then
                [ "$UNIT_INSTALLED" = "yes" ] && printf "%s enabled enabled\\n" "$2"
                return 0
            fi
        }
        command() {
            if [ "\${2:-}" = "systemctl" ] || [ "\${2:-}" = "sudo" ]; then return 0; fi
            builtin command "$@"
        }
        '"$RESTART_FNS"'
        if require_managed_backend; then echo GATE_OK; else echo GATE_FAIL; fi
    ' 2>&1
}

OUT_GATE_BLOCKED=$(run_supervisor_gate no false)
if printf '%s\n' "$OUT_GATE_BLOCKED" | grep -q 'GATE_FAIL'; then
    ok "systemd 유닛이 없으면 기본 게이트가 실제로 배포를 차단한다"
else
    bad "systemd 유닛이 없으면 기본 게이트가 실제로 배포를 차단한다" "게이트 실행 결과: $OUT_GATE_BLOCKED"
fi

OUT_GATE_BYPASS=$(run_supervisor_gate no true)
if printf '%s\n' "$OUT_GATE_BYPASS" | grep -q 'GATE_OK'; then
    ok "ALLOW_UNMANAGED_BACKEND=true일 때만 게이트를 우회한다"
else
    bad "ALLOW_UNMANAGED_BACKEND=true일 때만 게이트를 우회한다" "게이트 실행 결과: $OUT_GATE_BYPASS"
fi

# 유닛 미설치 + 명시적 응급 우회 → 종전 경로
if printf '%s\n' "$OUT_NO_UNIT" | grep -q 'CALL bash .*stop\.sh' \
   && printf '%s\n' "$OUT_NO_UNIT" | grep -q 'CALL bash .*start\.sh'; then
    ok "유닛 미설치 시 stop.sh/start.sh 로 재시작한다"
else
    bad "유닛 미설치 시 stop.sh/start.sh 로 재시작한다" "실제 호출: $(printf '%s' "$OUT_NO_UNIT" | tr '\n' ' ')"
fi

if printf '%s\n' "$OUT_NO_UNIT" | grep -q 'systemctl restart'; then
    bad "유닛 미설치 시 systemctl 을 부르지 않는다" "미설치인데 systemctl restart 를 불렀습니다."
else
    ok "유닛 미설치 시 systemctl 을 부르지 않는다"
fi

# 유닛 설치 → systemctl 로 우회하고 start.sh 를 건너뛴다
if printf '%s\n' "$OUT_WITH_UNIT" | grep -q 'systemctl restart ongo-backend\.service'; then
    ok "유닛 설치 시 systemctl restart 로 재시작한다"
else
    bad "유닛 설치 시 systemctl restart 로 재시작한다" "실제 호출: $(printf '%s' "$OUT_WITH_UNIT" | tr '\n' ' ')"
fi

if printf '%s\n' "$OUT_WITH_UNIT" | grep -qE 'CALL bash .*(start|stop)\.sh'; then
    bad "유닛 설치 시 start.sh 를 우회한다" "유닛이 있는데 스크립트를 직접 불렀습니다 — 그 프로세스는 유닛 cgroup 밖에 남습니다."
else
    ok "유닛 설치 시 start.sh 를 우회한다"
fi

# (e) 유닛 파일이 실제 계약과 맞는가 (설치 전에는 아무 영향이 없는 정적 파일)
UNIT_FILE="$SCRIPT_DIR/ongo-backend.service"
if [ -f "$UNIT_FILE" ]; then
    ok "유닛 파일이 저장소에 있다"
    # start.sh 는 env 게이트·FFMPEG_PATH·기동검증을 한다. java 를 직접 부르면 전부 잃는다.
    if grep -qE '^ExecStart=.*deploy/start\.sh' "$UNIT_FILE" \
       && grep -qE '^ExecStop=.*deploy/stop\.sh' "$UNIT_FILE"; then
        ok "유닛이 start.sh/stop.sh 를 감싼다(java 직접 실행 아님)"
    else
        bad "유닛이 start.sh/stop.sh 를 감싼다(java 직접 실행 아님)" "ExecStart/ExecStop 이 스크립트를 가리키지 않습니다."
    fi
    # 운영 JVM 과 같은 주체여야 파일 소유·권한이 어긋나지 않는다.
    if grep -qE '^User=jenkins' "$UNIT_FILE" && grep -qE '^Group=jenkins' "$UNIT_FILE"; then
        ok "유닛이 현재 실행 주체(jenkins)를 유지한다"
    else
        bad "유닛이 현재 실행 주체(jenkins)를 유지한다" "User/Group 이 jenkins 가 아닙니다."
    fi
    # start.sh 의 PID 파일 계약과 일치해야 systemd 가 MAINPID 를 찾는다.
    if grep -qE '^Type=forking' "$UNIT_FILE" && grep -qE '^PIDFile=/data/ongo/app\.pid' "$UNIT_FILE"; then
        ok "Type=forking + PIDFile 이 start.sh 계약과 맞는다"
    else
        bad "Type=forking + PIDFile 이 start.sh 계약과 맞는다" "start.sh 는 setsid 후 app.pid 를 씁니다."
    fi
    # 설정 오류로 못 뜨는 상태를 무한 재시도로 감추지 않는다.
    if grep -qE '^Restart=on-failure' "$UNIT_FILE" && grep -qE '^StartLimitBurst=' "$UNIT_FILE"; then
        ok "재시작 상한이 있어 설정 오류를 감추지 않는다"
    else
        bad "재시작 상한이 있어 설정 오류를 감추지 않는다" "Restart=on-failure 와 StartLimitBurst 가 필요합니다."
    fi

    # StartLimit* 은 **[Unit] 섹션 키다** (systemd v230 에서 [Service] 로부터 이동).
    #
    # 키가 존재하는지만 보면 이 결함을 놓친다. [Service] 에 두면 StartLimitBurst 는
    # 레거시 별칭으로 조용히 수용되지만 StartLimitIntervalSec 은 **무시된다** —
    # 간격이 기본값 10 초로 떨어지고, RestartSec=15s 라 "10 초 안에 3 회" 가 영원히
    # 성립하지 않아 **상한이 사실상 사라진다.** 그래서 섹션까지 고정한다.
    unit_section_of() {
        # 주어진 키가 속한 [섹션] 이름. 없으면 빈 문자열.
        awk -v key="$1" '
            /^\[.*\]$/ { section = $0; next }
            $0 ~ "^" key "=" { print section; exit }
        ' "$UNIT_FILE"
    }
    for key in StartLimitIntervalSec StartLimitBurst; do
        section=$(unit_section_of "$key")
        case "$section" in
            "[Unit]")
                ok "$key 가 [Unit] 섹션에 있다" ;;
            "")
                bad "$key 가 [Unit] 섹션에 있다" "$key 가 유닛 파일에 없습니다." ;;
            *)
                bad "$key 가 [Unit] 섹션에 있다" \
                    "$section 에 있습니다. systemd 가 무시하거나(IntervalSec) 레거시로만 수용해(Burst) 상한이 무력화됩니다." ;;
        esac
    done
    # EnvironmentFile 로 .env 를 직접 읽으면 셸 source 와 파싱이 갈라질 수 있다.
    if grep -qE '^EnvironmentFile=' "$UNIT_FILE"; then
        bad "EnvironmentFile 로 .env 를 직접 읽지 않는다" "start.sh 의 env 게이트를 우회하고 파싱이 갈라집니다."
    else
        ok "EnvironmentFile 로 .env 를 직접 읽지 않는다"
    fi
else
    bad "유닛 파일이 저장소에 있다" "$UNIT_FILE 이 없습니다."
fi

# (f) 문법
if bash -n "$TARGET" 2>/dev/null; then
    ok "bash -n 통과"
else
    bad "bash -n 통과" "$(bash -n "$TARGET" 2>&1 | head -3)"
fi

# ---------------------------------------------------------------------------
echo
printf '통과 %d / 실패 %d\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
