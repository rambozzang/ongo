#!/usr/bin/env bash
#
# preflight-schema.sh 단위 테스트.
#
# ## 격리
#
# **운영은 물론 어떤 실제 DB 에도 접속하지 않는다.** 임시 디렉터리에 mock `psql` 실행
# 파일을 만들고 PATH 앞에 두어, 스크립트가 부르는 psql 을 통째로 갈아끼운다. mock 은
# 질의 문자열을 보고 미리 정한 답만 돌려주며 아무 데도 연결하지 않는다.
#
# ## 왜 필요한가
#
# 이 스크립트가 fail-open 이면 배포 게이트가 있으나 마나다. 그런데 그 동작은 psql 이
# 없거나 접속이 안 되는 상황이라 평소 실행 경로에서 드러나지 않는다. mock 으로
# 그 상황을 만들어 종료 코드를 고정한다.
#
# 실행: bash deploy/preflight-schema.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/preflight-schema.sh"

PASS=0
FAIL=0

pass() { echo "  ok   - $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL - $1"; echo "         $2"; FAIL=$((FAIL + 1)); }

# mock psql 을 만든다.
#
# $1 시나리오:
#   ok            정상 — V103 성공, 스키마 존재
#   no-v103       접속은 되지만 V103 이력이 없음(운영 현재 상태: V93)
#   no-column     V103 이력은 있는데 컬럼이 없음(수동 삭제 등)
#   conn-fail     접속 실패
make_mock_psql() {
    local scenario="$1"
    local dir="$2"
    cat > "$dir/psql" <<MOCK
#!/usr/bin/env bash
# 호출 인자를 기록해 테스트가 접속 문자열을 검사할 수 있게 한다.
echo "\$@" >> "$dir/psql-args.log"

scenario="$scenario"
query=""
while [ \$# -gt 0 ]; do
  if [ "\$1" = "-c" ]; then query="\$2"; fi
  shift
done

if [ "\$scenario" = "conn-fail" ]; then
  echo "psql: could not connect to server" >&2
  exit 2
fi

case "\$query" in
  "SELECT 1")                       echo "1" ;;
  *"ORDER BY installed_rank"*)
      if [ "\$scenario" = "no-v103" ]; then echo "93"; else echo "103"; fi ;;
  *"WHERE version = '103'"*)
      if [ "\$scenario" = "no-v103" ]; then echo ""; else echo "1"; fi ;;
  *information_schema*)
      if [ "\$scenario" = "no-column" ]; then echo ""; else echo "1"; fi ;;
  *) echo "" ;;
esac
exit 0
MOCK
    chmod +x "$dir/psql"
}

# 시나리오 하나를 실행하고 종료 코드를 돌려준다.
run_with() {
    local scenario="$1"
    shift
    local dir
    dir="$(mktemp -d)"
    LAST_TMP="$dir"

    if [ "$scenario" != "no-psql" ]; then
        make_mock_psql "$scenario" "$dir"
    fi

    # PATH 를 mock 만 있는 디렉터리 + 기본 도구로 좁힌다. no-psql 이면 psql 이 없다.
    env -i \
        PATH="$dir:/usr/bin:/bin" \
        HOME="$dir" \
        "$@" \
        bash "$TARGET" > "$dir/out.log" 2>&1
    return $?
}

echo "preflight-schema.sh"

# ---- fail-closed: 점검 불가 ----

run_with no-psql DB_PASSWORD=secret
rc=$?
[ "$rc" -ne 0 ] \
    && pass "psql 이 없으면 0 이 아닌 코드로 끝난다 (rc=$rc)" \
    || fail "psql 이 없어도 통과했다" "rc=$rc"

run_with conn-fail DB_PASSWORD=secret
rc=$?
[ "$rc" -ne 0 ] \
    && pass "접속에 실패하면 0 이 아닌 코드로 끝난다 (rc=$rc)" \
    || fail "접속 실패인데 통과했다" "rc=$rc"

# DB_PASSWORD 는 application.yml 에도 기본값이 없다. 비어 있으면 접속이 될 리 없다.
run_with ok
rc=$?
[ "$rc" -ne 0 ] \
    && pass "DB_PASSWORD 가 없으면 0 이 아닌 코드로 끝난다 (rc=$rc)" \
    || fail "DB_PASSWORD 없이 통과했다" "rc=$rc"

# ---- 스키마 판정 ----

run_with no-v103 DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "V103 이력이 없으면 rc=1 로 중단한다" \
    || fail "V103 없음을 rc=1 로 알리지 않았다" "rc=$rc"
grep -q "V94~V103" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 V94~V103 선행 적용을 명시한다" \
    || fail "중단 메시지에 V94~V103 안내가 없다" "$(tail -3 "$LAST_TMP/out.log")"

run_with no-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "이력은 있어도 컬럼이 없으면 rc=1 로 중단한다" \
    || fail "컬럼 누락을 잡지 못했다" "rc=$rc"

run_with ok DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 0 ] \
    && pass "V103 적용 + 스키마 존재면 rc=0 이다" \
    || fail "정상인데 통과하지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"

# ---- DB_URL 기본값 ----

# application.yml 의 spring.datasource.url 과 같은 기본을 써야 한다.
# 여기서만 다른 기본을 쓰면 점검한 DB 와 애플리케이션이 붙는 DB 가 갈라진다.
run_with ok DB_PASSWORD=secret
grep -q "postgresql://localhost:54332/ongo" "$LAST_TMP/psql-args.log" \
    && pass "DB_URL 생략 시 application.yml 과 같은 기본값을 쓴다" \
    || fail "DB_URL 기본값이 application.yml 과 다르다" "$(head -1 "$LAST_TMP/psql-args.log")"

grep -q "jdbc:" "$LAST_TMP/psql-args.log" \
    && fail "psql 에 jdbc: 접두사가 그대로 넘어갔다" "$(head -1 "$LAST_TMP/psql-args.log")" \
    || pass "jdbc: 접두사를 떼고 psql 에 넘긴다"

run_with ok DB_PASSWORD=secret DB_URL="jdbc:postgresql://db.example:5432/prod"
grep -q "postgresql://db.example:5432/prod" "$LAST_TMP/psql-args.log" \
    && pass "DB_URL 이 있으면 그 값을 쓴다" \
    || fail "지정한 DB_URL 을 쓰지 않았다" "$(head -1 "$LAST_TMP/psql-args.log")"

# ---- 비밀번호 노출 ----

run_with ok DB_PASSWORD=super-secret-value
grep -q "super-secret-value" "$LAST_TMP/psql-args.log" \
    && fail "psql 인자에 비밀번호가 실렸다" "$(head -1 "$LAST_TMP/psql-args.log")" \
    || pass "비밀번호를 psql 인자로 넘기지 않는다"

echo
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
