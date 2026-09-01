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
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PASS=0
FAIL=0

pass() { echo "  ok   - $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL - $1"; echo "         $2"; FAIL=$((FAIL + 1)); }

# mock psql 을 만든다.
#
# $1 시나리오:
#   ok            정상 — V108 성공, 스키마 존재
#   no-v104       접속은 되지만 V110 이력이 없음(운영 현재 상태: V93)
#   no-column     V110 이력이 있는데 컬럼이 없음(수동 삭제 등)
#   no-pipeline-column
#                 다른 컬럼은 있는데 ai_pipeline_jobs.refunded_credits 만 없음.
#                 V106 을 REQUIRED_SCHEMA 에 더한 것이 실제로 검사되는지 고정한다 —
#                 버전 이력만 보는 검사는 이 경우를 통과시킨다.
#   no-revenue-column
#                 다른 컬럼은 있는데 analytics_daily.revenue_status 만 없음.
#                 V107 에 대해 같은 것을 고정한다.
#   no-allocation-column
#                 다른 컬럼은 있는데 ai_pipeline_jobs.credit_allocation 만 없음.
#                 V108 에 대해 같은 것을 고정한다. 이 컬럼이 없으면 파이프라인 시작이
#                 SQL 오류로 실패하고, 우회하면 구매 크레딧이 무료 크레딧으로 바뀐다.
#   no-v113       V112 까지는 적용됐는데 V113 이 없음. 요구 버전을 올리지 않으면
#                 이 시나리오가 통과해 버려, preflight 가 최신 마이그레이션을 놓친다.
#   no-content-image-key
#                 다른 컬럼은 있는데 content_images.storage_object_key 만 없음(V112).
#                 이 컬럼이 없으면 이미지 게시가 SQL 오류로 실패한다.
#   no-url-text   컬럼은 다 있는데 URL 컬럼이 아직 VARCHAR(500) 임(V113 미적용).
#                 존재 검사로는 잡히지 않는 유일한 경우다.
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
  # 실패 이력 질의를 **가장 먼저** 가른다. 이 질의는 집계 안에 ORDER BY installed_rank 가
  # 들어 있어, 아래 "마지막 성공 버전" 분기와 패턴이 겹친다. string_agg 는 이 질의에만
  # 있으므로 분기 순서가 바뀌어도 오분류되지 않는다.
  *string_agg*)
      if [ "\$scenario" = "failed-migration" ]; then echo "105, 106"; else echo ""; fi ;;
  *"credit_tx_type"*)
      if [ "\$scenario" = "no-revoke-enum" ]; then echo ""; else echo "1"; fi ;;
  *"ORDER BY installed_rank"*)
      case "\$scenario" in
        no-v104) echo "93" ;;
        no-v113) echo "112" ;;
        *)       echo "113" ;;
      esac ;;
  # 버전 숫자를 박지 않는다. REQUIRED_FLYWAY_VERSION 을 올릴 때마다 mock 을 함께
  # 고쳐야 하면, 고치는 것을 잊은 순간 "ok" 시나리오가 엉뚱한 이유로 깨진다.
  *"FROM flyway_schema_history WHERE version = '"*)
      case "\$scenario" in
        no-v104|no-v113) echo "" ;;
        *)               echo "1" ;;
      esac ;;
  *"column_name = 'refunded_credits'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-pipeline-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'revenue_status'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-revenue-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'credit_allocation'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-allocation-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'claimed_at'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-claim-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'attempts'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-attempts-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'source'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-source-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'unavailable_metrics'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-unavailable-column" ]; then echo ""; else echo "1"; fi ;;
  *"column_name = 'storage_object_key'"*)
      if [ "\$scenario" = "no-column" ] || [ "\$scenario" = "no-content-image-key" ]; then echo ""; else echo "1"; fi ;;
  # V113 은 컬럼을 더하지 않고 길이 제한을 뗀다. 존재 검사와 분리된 질의라 따로 가른다.
  *"character_maximum_length IS NULL"*)
      if [ "\$scenario" = "no-url-text" ]; then echo ""; else echo "1"; fi ;;
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

run_with no-v104 DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "요구 버전 이력이 없으면 rc=1 로 중단한다" \
    || fail "요구 버전 없음을 rc=1 로 알리지 않았다" "rc=$rc"
grep -q "V94~V113" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 V94~V113 선행 적용을 명시한다" \
    || fail "중단 메시지에 V94~V113 안내가 없다" "$(tail -3 "$LAST_TMP/out.log")"

run_with no-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "이력은 있어도 컬럼이 없으면 rc=1 로 중단한다" \
    || fail "컬럼 누락을 잡지 못했다" "rc=$rc"

# V106 의 컬럼만 없는 경우.
#
# 버전 이력이 조작되거나 컬럼만 수동으로 지워지면 이력 검사는 통과한다. 새 컬럼을
# REQUIRED_SCHEMA 에 더한 것이 **실제로 검사되는지** 여기서 고정한다 — 목록에서 빠지면
# 이 테스트가 먼저 깨진다.
run_with no-pipeline-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "ai_pipeline_jobs.refunded_credits 가 없으면 rc=1 로 중단한다" \
    || fail "V106 컬럼 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "ai_pipeline_jobs.refunded_credits" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 컬럼 이름을 알려 준다" \
    || fail "어떤 컬럼이 없는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V107 의 컬럼만 없는 경우. 수익 상태 컬럼이 없으면 성과 동기화와 수익 조회가 통째로
# SQL 오류를 낸다. 버전 이력만 보는 검사는 이 경우를 통과시킨다.
run_with no-revenue-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "analytics_daily.revenue_status 가 없으면 rc=1 로 중단한다" \
    || fail "V107 컬럼 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "analytics_daily.revenue_status" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 수익 컬럼 이름을 알려 준다" \
    || fail "어떤 수익 컬럼이 없는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V108 의 컬럼만 없는 경우.
#
# **이번 배포가 실제로 막으려는 상황이다.** AiPipelineJooqRepository 가
# `credit_allocation` 을 SELECT/INSERT 하므로, 이력만 보고 통과시키면 서버는 기동한 뒤
# 파이프라인 시작에서 SQL 오류를 낸다. preflight 가 컬럼까지 봐야 하는 이유다.
run_with no-allocation-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "ai_pipeline_jobs.credit_allocation 이 없으면 rc=1 로 중단한다" \
    || fail "V108 컬럼 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "ai_pipeline_jobs.credit_allocation" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 차감 출처 컬럼 이름을 알려 준다" \
    || fail "어떤 컬럼이 없는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V109 의 claim 컬럼만 없는 경우. 없으면 복구 스캐너가 멈춘 번역을 되살리지 못해
# 고객이 크레딧만 잃는다. 이력만 보는 검사는 이 경우를 통과시킨다.
run_with no-claim-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "video_translations.claimed_at 이 없으면 rc=1 로 중단한다" \
    || fail "V109 컬럼 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "video_translations.claimed_at" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 claim 컬럼 이름을 알려 준다" \
    || fail "어떤 컬럼이 없는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V109 의 attempts 컬럼만 없는 경우. 저장소가 SELECT/UPDATE 하므로 없으면 번역 요청과
# 복구가 SQL 오류를 낸다. 재시도 상한도 판정할 수 없어 죽는 입력이 LLM 을 무한히 태운다.
run_with no-attempts-column DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "video_translations.attempts 가 없으면 rc=1 로 중단한다" \
    || fail "V109 attempts 컬럼 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "video_translations.attempts" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 attempts 컬럼 이름을 알려 준다" \
    || fail "어떤 컬럼이 없는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V110 의 컬럼만 없는 경우. 없으면 지표 동기화·캠페인 분석이 SQL 오류를 내고, 우회하면
# 플랫폼이 주지 않는 0 이 다시 측정값으로 합산돼 보상 판단이 왜곡된다.
for scenario in no-source-column no-unavailable-column; do
    run_with "$scenario" DB_PASSWORD=secret
    rc=$?
    [ "$rc" -eq 1 ] \
        && pass "$scenario 이면 rc=1 로 중단한다" \
        || fail "V110 컬럼 누락을 잡지 못했다($scenario)" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
    grep -q "ugc_post_metric_snapshots\." "$LAST_TMP/out.log" \
        && pass "$scenario 중단 메시지가 빠진 컬럼 이름을 알려 준다" \
        || fail "어떤 컬럼이 없는지 알려주지 않았다($scenario)" "$(tail -3 "$LAST_TMP/out.log")"
done

# ---- 부분 적용 감지 ----
#
# 마이그레이션이 도중에 깨지면 success=false 행이 남고 스키마는 반쯤 반영된 상태다.
# 마지막 **성공** 버전만 보는 검사는 이 경우를 통과시킨다 — 요구 버전이 성공으로 남아
# 있어도 그 뒤에 깨진 것이 있으면 배포해도 애플리케이션이 뜨지 않는다.
run_with failed-migration DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "실패한 마이그레이션이 남아 있으면 rc=1 로 중단한다" \
    || fail "부분 적용 상태를 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "105, 106" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 실패한 버전을 알려 준다" \
    || fail "어떤 버전이 실패했는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# ---- enum 값(V100) ----
#
# V100 은 컬럼이 아니라 credit_tx_type 에 값을 더한다. 컬럼 검사로는 잡히지 않는데,
# 이 값이 없으면 크레딧 회수가 마지막 단계에서 실패한다(결제 취소·환불 경로).
run_with no-revoke-enum DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "credit_tx_type 에 REVOKE 가 없으면 rc=1 로 중단한다" \
    || fail "V100 enum 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "REVOKE" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 enum 값을 알려 준다" \
    || fail "어떤 enum 값이 없는지 알려주지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

run_with ok DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 0 ] \
    && pass "요구 버전 적용 + 스키마 존재면 rc=0 이다" \
    || fail "정상인데 통과하지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"

# 점검 대상 목록 자체를 고정한다. 새 마이그레이션을 더하면서 여기를 빠뜨리면
# 배포 게이트가 그 컬럼을 보지 않는다.
grep -q "ai_pipeline_jobs:refunded_credits" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V106 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 ai_pipeline_jobs:refunded_credits 가 없다" ""
grep -q "analytics_daily:revenue_status" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V107 상태 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 analytics_daily:revenue_status 가 없다" ""
grep -q "analytics_daily:revenue_currency" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V107 통화 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 analytics_daily:revenue_currency 가 없다" ""
grep -q "ai_pipeline_jobs:credit_allocation" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V108 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 ai_pipeline_jobs:credit_allocation 이 없다" ""
grep -q "video_translations:credit_allocation" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V109 번역 출처 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 video_translations:credit_allocation 이 없다" ""
grep -q "video_translations:claimed_at" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V109 claim 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 video_translations:claimed_at 이 없다" ""
grep -q "video_translations:attempts" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V109 attempts 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 video_translations:attempts 가 없다" ""
grep -q "ugc_post_metric_snapshots:source" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V110 출처 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 ugc_post_metric_snapshots:source 가 없다" ""
grep -q "ugc_post_metric_snapshots:unavailable_metrics" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V110 미측정 목록 컬럼을 포함한다" \
    || fail "REQUIRED_SCHEMA 에 ugc_post_metric_snapshots:unavailable_metrics 가 없다" ""
# ---- V111~V113 ----

# V112 까지 적용됐는데 V113 이 없는 경우.
#
# 요구 버전을 올리지 않으면 이 시나리오가 **통과해 버린다.** 그러면 URL 컬럼이 아직
# VARCHAR(500) 인 서버에 새 코드가 올라가고, 한글 파일명 에셋 업로드가 런타임에 실패한다.
run_with no-v113 DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "V113 이 없으면 rc=1 로 중단한다" \
    || fail "V113 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "112" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 현재 버전을 알려 준다" \
    || fail "현재 버전을 알리지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V112 의 컬럼만 없는 경우. 이력이 조작되거나 컬럼만 수동으로 지워지면 버전 검사는
# 통과한다. 이 컬럼이 없으면 이미지 게시가 SQL 오류로 실패한다.
run_with no-content-image-key DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "content_images.storage_object_key 누락을 잡는다" \
    || fail "V112 컬럼 누락을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "content_images.storage_object_key" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 빠진 이미지 키 컬럼을 알려 준다" \
    || fail "빠진 컬럼을 알리지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# V113 은 컬럼을 더하지 않고 **길이 제한을 뗀다.** 존재 검사로는 잡히지 않는 유일한
# 경우라, 타입 검사가 실제로 도는지 여기서 고정한다.
run_with no-url-text DB_PASSWORD=secret
rc=$?
[ "$rc" -eq 1 ] \
    && pass "URL 컬럼이 아직 VARCHAR 면 rc=1 로 중단한다" \
    || fail "V113 타입 미적용을 잡지 못했다" "rc=$rc / $(tail -3 "$LAST_TMP/out.log")"
grep -q "assets.file_url" "$LAST_TMP/out.log" \
    && pass "중단 메시지가 좁은 URL 컬럼을 알려 준다" \
    || fail "좁은 컬럼을 알리지 않았다" "$(tail -3 "$LAST_TMP/out.log")"

# 새 컬럼이 REQUIRED_SCHEMA 에 실제로 들어 있는지 — 목록에서 빠지면 위 시나리오가
# 통과해 버리므로 함께 고정한다.
grep -q "ugc_shorts_run_stages:credit_allocation" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V111 분해 컬럼을 포함한다" \
    || fail "V111 분해 컬럼이 REQUIRED_SCHEMA 에 없다" "$(grep REQUIRED_SCHEMA "$TARGET")"
grep -q "ugc_shorts_run_stages:refunded_credits" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V111 정산 표식 컬럼을 포함한다" \
    || fail "V111 정산 표식 컬럼이 REQUIRED_SCHEMA 에 없다" "$(grep REQUIRED_SCHEMA "$TARGET")"
grep -q "content_images:storage_object_key" "$TARGET" \
    && pass "REQUIRED_SCHEMA 가 V112 컬럼을 포함한다" \
    || fail "V112 컬럼이 REQUIRED_SCHEMA 에 없다" "$(grep REQUIRED_SCHEMA "$TARGET")"
grep -q "brand_kits:watermark_url" "$TARGET" \
    && pass "URL 타입 검사가 brand_kits 네 컬럼을 포함한다" \
    || fail "brand_kits URL 컬럼이 타입 검사에 없다" "$(grep REQUIRED_TEXT_COLUMNS "$TARGET")"

# 요구 버전은 **저장소의 마지막 마이그레이션과 같아야 한다.**
#
# 숫자를 여기에 박으면 마이그레이션을 더할 때마다 두 곳을 고쳐야 하고, 한쪽을 잊으면
# preflight 가 최신 스키마를 놓친 채 통과한다. 그래서 파일 목록에서 실제 최신 버전을
# 읽어 비교한다. `V10_2` 같은 소수 버전이 섞여 있어 문자열 정렬은 쓸 수 없다.
LATEST_MIGRATION="$(
    ls "$REPO_ROOT/backend/onGo-api/src/main/resources/db/migration" \
        | sed -n 's/^V\([0-9][0-9_]*\)__.*/\1/p' \
        | tr '_' '.' \
        | sort -V \
        | tail -1
)"
grep -q "REQUIRED_FLYWAY_VERSION=\"$LATEST_MIGRATION\"" "$TARGET" \
    && pass "REQUIRED_FLYWAY_VERSION 이 최신 마이그레이션(V$LATEST_MIGRATION)과 같다" \
    || fail "REQUIRED_FLYWAY_VERSION 이 최신 마이그레이션(V$LATEST_MIGRATION)과 다르다" "$(grep REQUIRED_FLYWAY_VERSION "$TARGET")"

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
