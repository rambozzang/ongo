#!/usr/bin/env bash
#
# migrate-schema.sh 단위 테스트.
#
# ## 격리
#
# **어떤 실제 DB 에도, 어떤 실제 JVM 에도 붙지 않는다.** 임시 디렉터리에 fake `java`
# 실행 파일을 만들어 PATH 앞에 두고, JAR·로그·.env 경로도 전부 임시 디렉터리로 돌린다.
# fake java 는 시나리오에 따라 로그를 쓰고 살아 있거나 죽는 흉내만 낸다.
#
# ## 왜 필요한가
#
# 이 스크립트가 fail-open 이면 마이그레이션이 안 됐는데 배포가 계속된다. 그러면
# preflight 가 다시 막아 주긴 하지만, "적용했다"는 거짓 보고가 로그에 남아 원인 추적이
# 어긋난다. 더 나쁜 것은 조기 종료·타임아웃을 성공으로 보는 경우다 — 그때는 스키마가
# 반쯤 반영된 채 배포가 진행될 수 있다.
#
# 실행: bash deploy/migrate-schema.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/migrate-schema.sh"

PASS=0
FAIL=0
pass() { echo "  ok   - $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL - $1"; echo "         $2"; FAIL=$((FAIL + 1)); }

# fake java 를 만든다.
#
# $1 시나리오:
#   ok            기동 완료 문구를 남기고 계속 살아 있음(스크립트가 내려야 함)
#   self-exit     문구를 남기고 스스로 정상 종료(web-application-type=none 정상 경로)
#   early-exit    문구 없이 즉시 종료(마이그레이션 실패)
#   fail-log      Flyway 오류를 남기고 종료
#   hang          아무 문구도 없이 계속 살아 있음(타임아웃)
make_fake_java() {
    local scenario="$1"
    local dir="$2"
    cat > "$dir/java" <<FAKE
#!/usr/bin/env bash
# 넘어온 JVM 인자를 기록해 테스트가 검사할 수 있게 한다.
echo "\$@" > "$dir/java-args.log"

scenario="$scenario"

case "\$scenario" in
  ok)
      echo "Started OnGoApplication in 12.345 seconds (process running for 13.1)"
      # 살아 있는 채로 대기한다. 스크립트가 TERM 을 보내 내려야 한다.
      trap 'echo "terminated"; exit 143' TERM
      while true; do sleep 0.2; done
      ;;
  self-exit)
      echo "Started OnGoApplication in 9.1 seconds (process running for 9.6)"
      exit 0
      ;;
  early-exit)
      echo "org.springframework.beans.factory.BeanCreationException: datasource"
      exit 1
      ;;
  fail-log)
      echo "FlywayValidateException: Migration checksum mismatch for version 105"
      exit 1
      ;;
  hang)
      trap 'exit 143' TERM
      while true; do sleep 0.2; done
      ;;
esac
FAKE
    chmod +x "$dir/java"
}

# 시나리오 하나를 실행하고 종료 코드를 돌려준다.
run_with() {
    local scenario="$1"
    shift
    local dir
    dir="$(mktemp -d)"
    LAST_TMP="$dir"

    mkdir -p "$dir/jar" "$dir/log"
    # JAR 은 존재만 확인하므로 빈 파일로 충분하다.
    : > "$dir/jar/ongo-api.jar"

    # required-env.sh 가 요구하는 변수를 채운 .env 를 만든다. 실제 값이 아니라
    # 길이 검사만 통과하는 더미이며, fake java 는 DB 에 접속하지 않는다.
    {
        echo "DB_URL=jdbc:postgresql://localhost:54332/ongo"
        echo "DB_USERNAME=ongo"
        echo "DB_PASSWORD=not-a-real-password"
        echo "JWT_SECRET=0123456789012345678901234567890123456789"
        echo "PLATFORM_TOKEN_ENCRYPTION_KEY=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
    } > "$dir/.env"
    # required-env.sh 의 목록이 늘어나도 테스트가 그것 때문에 깨지지 않도록,
    # 실제 필수 목록을 읽어 빠진 값을 더미로 채운다.
    (
        source "$SCRIPT_DIR/required-env.sh" 2>/dev/null || exit 0
        set -a; source "$dir/.env"; set +a
        for v in $(ongo_missing_env_vars 2>/dev/null); do
            echo "$v=dummy-value-long-enough-for-checks"
        done
    ) >> "$dir/.env"

    if [ "$scenario" != "no-java" ]; then
        make_fake_java "$scenario" "$dir"
    fi

    env -i \
        PATH="$dir:/usr/bin:/bin" \
        HOME="$dir" \
        ONGO_BASE_DIR="$dir" \
        ONGO_JAR_PATH="$dir/jar/ongo-api.jar" \
        ONGO_LOG_DIR="$dir/log" \
        ONGO_ENV_FILE="$dir/.env" \
        "$@" \
        bash "$TARGET" > "$dir/out.log" 2>&1
    return $?
}

# 마이그레이션 JVM 이 사라질 때까지 잠깐 기다린다.
#
# 고정 `sleep 1` 로 판정하면 종료 신호와 프로세스 소멸 사이의 짧은 간격 때문에 결과가
# 실행마다 달라진다. 그 흔들림은 "가끔 실패하는 테스트"로 보이고, 결국 아무도 믿지 않게
# 된다. 사라지는 즉시 통과하고, 끝까지 남아 있을 때만 실패로 본다.
wait_for_no_migration_jvm() {
    local waited=0
    while [ "$waited" -lt 50 ]; do
        pgrep -f "ongo-migrate-marker" >/dev/null 2>&1 || return 0
        sleep 0.1
        waited=$((waited + 1))
    done
    return 1
}

echo "migrate-schema.sh"

# ---- fail-closed: 실행 불가 ----

run_with no-java
rc=$?
[ "$rc" -ne 0 ] \
    && pass "java 가 없으면 0 이 아닌 코드로 끝난다 (rc=$rc)" \
    || fail "java 없이도 성공으로 끝났다" "rc=$rc"

# JAR 이 없으면 마이그레이션할 대상이 없다.
dir="$(mktemp -d)"
mkdir -p "$dir/log"
: > "$dir/.env"
make_fake_java ok "$dir"
env -i PATH="$dir:/usr/bin:/bin" HOME="$dir" \
    ONGO_BASE_DIR="$dir" ONGO_JAR_PATH="$dir/jar/missing.jar" \
    ONGO_LOG_DIR="$dir/log" ONGO_ENV_FILE="$dir/.env" \
    bash "$TARGET" > "$dir/out.log" 2>&1
rc=$?
[ "$rc" -ne 0 ] \
    && pass "JAR 이 없으면 0 이 아닌 코드로 끝난다 (rc=$rc)" \
    || fail "JAR 없이 성공으로 끝났다" "rc=$rc"

# ---- 실패는 절대 성공으로 보고하지 않는다 ----

run_with early-exit
rc=$?
[ "$rc" -eq 1 ] \
    && pass "기동 전에 종료하면 rc=1 로 끝난다" \
    || fail "조기 종료를 성공으로 봤다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "적용되지 않았습니다" "$LAST_TMP/out.log" \
    && pass "조기 종료 시 적용되지 않았음을 명시한다" \
    || fail "조기 종료 메시지가 없다" "$(tail -5 "$LAST_TMP/out.log")"
grep -q "BeanCreationException" "$LAST_TMP/out.log" \
    && pass "실패 시 로그 tail 을 남긴다" \
    || fail "실패 로그를 보여주지 않았다" "$(tail -5 "$LAST_TMP/out.log")"

run_with fail-log
rc=$?
[ "$rc" -ne 0 ] \
    && pass "Flyway 오류로 죽으면 0 이 아닌 코드로 끝난다 (rc=$rc)" \
    || fail "Flyway 실패를 성공으로 봤다" "rc=$rc"
grep -q "FlywayValidateException" "$LAST_TMP/out.log" \
    && pass "Flyway 오류 내용을 로그 tail 로 보여 준다" \
    || fail "Flyway 오류를 보여주지 않았다" "$(tail -5 "$LAST_TMP/out.log")"

# 제한 시간을 넘기면 rc=2. 기다림 자체가 성공 근거가 될 수 없다.
run_with hang MIGRATE_TIMEOUT_SECONDS=3
rc=$?
[ "$rc" -eq 2 ] \
    && pass "제한 시간을 넘기면 rc=2 로 끝난다" \
    || fail "타임아웃을 성공으로 봤다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"

# 타임아웃 뒤 우리가 띄운 JVM 이 남으면 서비스와 동시에 두 인스턴스가 DB 를 잡는다.
wait_for_no_migration_jvm \
    && pass "타임아웃이어도 마이그레이션 JVM 을 남기지 않는다" \
    || fail "타임아웃 후에도 마이그레이션 JVM 이 살아 있다" "$(pgrep -af 'ongo-migrate-marker')"

# ---- 성공 ----

run_with ok MIGRATE_TIMEOUT_SECONDS=20
rc=$?
[ "$rc" -eq 0 ] \
    && pass "기동 완료 문구를 확인하면 rc=0 이다" \
    || fail "정상인데 실패로 끝났다" "rc=$rc / $(tail -5 "$LAST_TMP/out.log")"

wait_for_no_migration_jvm \
    && pass "성공하면 마이그레이션 JVM 을 정상 종료한다" \
    || fail "성공 후에도 마이그레이션 JVM 이 살아 있다" "$(pgrep -af 'ongo-migrate-marker')"

# web-application-type=none 이면 컨텍스트 준비 후 스스로 끝나는 것이 정상 경로다.
run_with self-exit MIGRATE_TIMEOUT_SECONDS=20
rc=$?
[ "$rc" -eq 0 ] \
    && pass "기동 문구를 남기고 스스로 종료해도 rc=0 이다" \
    || fail "정상 자체 종료를 실패로 봤다" "rc=$rc / $(tail -5 "$LAST_TMP/out.log")"

# ---- JVM 인자: 서비스와 동시에 떠도 안전해야 한다 ----

run_with ok MIGRATE_TIMEOUT_SECONDS=20 >/dev/null 2>&1
ARGS="$(cat "$LAST_TMP/java-args.log" 2>/dev/null)"

grep -q "ongo.scheduling.enabled=false" <<< "$ARGS" \
    && pass "스케줄러를 끈다 — 살아 있는 서비스와 배치가 동시에 돌면 안 된다" \
    || fail "ongo.scheduling.enabled=false 가 없다" "$ARGS"

grep -q "spring.main.web-application-type=none" <<< "$ARGS" \
    && pass "웹 스택을 띄우지 않는다" \
    || fail "web-application-type=none 이 없다" "$ARGS"

grep -q "server.port=0" <<< "$ARGS" \
    && pass "운영 포트를 빼앗지 않는다" \
    || fail "server.port=0 이 없다" "$ARGS"

grep -q "spring.profiles.active=prod" <<< "$ARGS" \
    && pass "운영과 같은 프로파일로 실행한다 — 다른 DB 에 적용되면 안 된다" \
    || fail "prod 프로파일이 아니다" "$ARGS"

echo
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ]
