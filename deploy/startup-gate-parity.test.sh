#!/usr/bin/env bash
#
# 배포 사전검사와 **백엔드 기동 검증기**가 같은 것을 요구하는지 고정한다.
#
# ## 무엇이 깨져 있었나
#
# `required-env.sh` 헤더는 "한쪽에만 변수를 추가하면 선행 검증을 통과하고 기동
# 단계에서 죽는다" 고 적어 두었다. 그 드리프트를 `deploy.sh` ↔ `start.sh` 사이에서는
# 막았지만, **셸 목록 ↔ Kotlin 검증기** 사이는 아무도 보지 않았다.
#
# 실제로 벌어진 일:
#
#   ProductionConfigurationValidator 가 `spring.ai.openai.api-key` 를 강제한다
#   (쇼츠 STT 가 OpenAI 전용이라 다른 제공자로 대체되지 않는다).
#   application.yml 기본값은 `dummy-openai-key` 이고 검증기는 "dummy" 를 거부한다.
#   그런데 `OPENAI_API_KEY` 는 배포 필수 목록에 없었다.
#
#   결과: **배포는 초록불, 서비스는 중지된 뒤 기동 실패, 외부는 전량 502.**
#   실패 사유는 `start.sh` 가 매 기동마다 비우는 backend.log 안에만 남는다.
#
# ## 이 테스트가 무엇을 보는가
#
# Kotlin 검증기에서 `requireReal("...")` 대상을 **직접 읽어** 아래 매핑 표와 맞춘다.
# 검증기에 새 항목이 생기면 표에 없어서 실패한다 — 그때 배포 목록도 함께 고치게 된다.
# 표를 테스트에 두는 이유는 설정 키(`storage.s3.access-key`)와 환경변수 이름
# (`R2_ACCESS_KEY`)이 다르고, 그 연결이 application.yml 안에 흩어져 있기 때문이다.
#
# 실행: bash deploy/startup-gate-parity.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

VALIDATOR="$REPO_ROOT/backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/config/ProductionConfigurationValidator.kt"
REQUIRED_ENV="$REPO_ROOT/deploy/required-env.sh"
DEPLOY_SH="$REPO_ROOT/deploy/deploy.sh"
APP_YML="$REPO_ROOT/backend/onGo-api/src/main/resources/application.yml"

PASS=0
FAIL=0
pass() { PASS=$((PASS + 1)); echo "  ok   - $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL - $1"; [ $# -gt 1 ] && [ -n "$2" ] && echo "         $2"; }

echo "기동 게이트 패리티"

# ---- 검증기의 requireReal 대상 → 배포 환경변수 매핑 ----
#
# 왼쪽은 Kotlin 의 requireReal 인자 그대로, 오른쪽은 그 값을 공급하는 환경변수.
# `-` 는 "다른 값에서 파생되므로 별도 변수가 없다" 는 뜻이다.
gate_env_for() {
    case "$1" in
        "cors.allowed-origins (or APP_BASE_URL)")   echo "APP_BASE_URL" ;;
        "storage.bucket")                           echo "R2_BUCKET" ;;
        "storage.s3.endpoint")                      echo "R2_ACCOUNT_ID" ;;
        "storage.s3.access-key")                    echo "R2_ACCESS_KEY" ;;
        "storage.s3.secret-key")                    echo "R2_SECRET_KEY" ;;
        "payment.portone.store-id")                 echo "PORTONE_STORE_ID" ;;
        "payment.portone.channel-key")              echo "PORTONE_CHANNEL_KEY" ;;
        "payment.portone.api-secret")               echo "PORTONE_API_SECRET" ;;
        "payment.portone.webhook-secret")           echo "PORTONE_WEBHOOK_SECRET" ;;
        "google OAuth client-id")                   echo "GOOGLE_CLIENT_ID" ;;
        "google OAuth client-secret")               echo "GOOGLE_CLIENT_SECRET" ;;
        "kakao OAuth client-id")                    echo "KAKAO_CLIENT_ID" ;;
        "kakao OAuth client-secret")                echo "KAKAO_CLIENT_SECRET" ;;
        "OAUTH_STATE_SECRET")                       echo "OAUTH_STATE_SECRET" ;;
        # APP_BASE_URL 에서 파생된다(application.yml 의 public-api.oauth.callback-url).
        # 존재 검사는 APP_BASE_URL 이 대신하고, https 스킴은 별도 규칙이 본다.
        "public-api.oauth.callback-url")            echo "-" ;;
        "spring.ai.openai.api-key (Shorts transcription)") echo "OPENAI_API_KEY" ;;
        *)                                          echo "" ;;
    esac
}

if [ ! -f "$VALIDATOR" ]; then
    fail "기동 검증기 파일을 찾을 수 없다" "$VALIDATOR"
    echo ""
    echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
    exit 1
fi

# BSD/GNU 차이를 타지 않도록 grep -o 로 뽑는다.
SUBJECTS="$(grep -o 'requireReal("[^"]*"' "$VALIDATOR" | sed 's/requireReal("//; s/"$//')"

# 하나도 못 뽑으면 아래 루프가 통째로 건너뛰어 공허하게 통과한다. 먼저 막는다.
SUBJECT_COUNT="$(printf '%s\n' "$SUBJECTS" | grep -c . || true)"
[ "$SUBJECT_COUNT" -gt 0 ] \
    && pass "기동 검증기에서 requireReal 대상 ${SUBJECT_COUNT}건을 읽었다" \
    || fail "requireReal 대상을 하나도 읽지 못했다 — 검증기 형식이 바뀌었다" ""

REQUIRED_LIST="$(grep '^ONGO_REQUIRED_ENV_VARS=' "$REQUIRED_ENV" | cut -d'"' -f2)"
[ -n "$REQUIRED_LIST" ] \
    && pass "배포 필수 목록을 읽었다" \
    || fail "ONGO_REQUIRED_ENV_VARS 를 읽지 못했다 — required-env.sh 형식이 바뀌었다" ""

# ---- **핵심.** 기동이 요구하는 값이 전부 배포 사전검사에도 있는가 ----

UNMAPPED=""
UNGUARDED=""
while IFS= read -r subject; do
    [ -n "$subject" ] || continue
    env_name="$(gate_env_for "$subject")"

    if [ -z "$env_name" ]; then
        UNMAPPED="$UNMAPPED|$subject"
        continue
    fi
    [ "$env_name" = "-" ] && continue

    case " $REQUIRED_LIST " in
        *" $env_name "*) ;;
        *) UNGUARDED="$UNGUARDED $env_name($subject)" ;;
    esac
done <<< "$SUBJECTS"

# 매핑 표에 없는 새 항목 = 이 테스트가 판단할 수 없는 항목. 통과시키면 fail-open 이다.
[ -z "$UNMAPPED" ] \
    && pass "검증기의 모든 requireReal 대상이 매핑 표에 있다" \
    || fail "매핑 표에 없는 기동 요구 항목이 있다 — 표와 배포 목록을 함께 갱신할 것" \
            "$(printf '%s' "$UNMAPPED" | tr '|' '\n' | sed 's/^/           /')"

[ -z "$UNGUARDED" ] \
    && pass "기동이 요구하는 값이 모두 배포 사전검사 목록에 있다" \
    || fail "배포는 통과하지만 기동에서 죽는 변수가 있다 — 서비스가 중지된 채 502 가 된다" \
            "$UNGUARDED"

# ---- 검증기의 추가 규칙(길이·스킴)을 배포도 보는가 ----

# 검증기가 OAUTH_STATE_SECRET 에 32자를 건다. 배포 일반 규칙은 8자라 그 사이가 빈다.
#
# requireShort 인자 개수에 기대지 않고 조건식을 직접 찾는다. 앞서 이 값을
# `requireReal("OAUTH_STATE_SECRET")` 다음 줄에서 찾으려다 실패했다 — 실제 호출은
# 인자가 둘(`requireReal("OAUTH_STATE_SECRET", oauthStateSecret)`)이라 패턴이 어긋났다.
VALIDATOR_MIN="$(grep -o 'oauthStateSecret\.length >= [0-9][0-9]*' "$VALIDATOR" \
    | grep -o '[0-9][0-9]*' | head -1)"
DEPLOY_MIN="$(grep -o 'ONGO_MIN_LENGTH_OAUTH_STATE_SECRET=[0-9][0-9]*' "$REQUIRED_ENV" \
    | grep -o '[0-9][0-9]*' | head -1)"

if [ -n "$VALIDATOR_MIN" ] && [ -n "$DEPLOY_MIN" ]; then
    [ "$VALIDATOR_MIN" = "$DEPLOY_MIN" ] \
        && pass "OAUTH_STATE_SECRET 최소 길이가 양쪽 모두 ${VALIDATOR_MIN}자다" \
        || fail "OAUTH_STATE_SECRET 최소 길이가 다르다 — 검증기 ${VALIDATOR_MIN} vs 배포 ${DEPLOY_MIN}" ""
else
    fail "OAUTH_STATE_SECRET 최소 길이를 양쪽에서 읽지 못했다" \
         "검증기=${VALIDATOR_MIN:-없음} 배포=${DEPLOY_MIN:-없음}"
fi

# 검증기가 콜백 URL 에 https 를 요구한다. 그 URL 은 APP_BASE_URL 에서 파생된다.
grep -q 'publicOAuthCallbackUrl.startsWith("https://")' "$VALIDATOR" \
    && { grep -q 'ongo_invalid_startup_gate_env_vars' "$REQUIRED_ENV" \
            && pass "콜백 URL https 규칙을 배포 사전검사도 본다" \
            || fail "검증기는 https 를 요구하는데 배포 사전검사에 해당 검사가 없다" ""; } \
    || pass "검증기에 콜백 URL https 규칙이 없다(검사 불필요)"

# 파생 관계가 깨지면 위 APP_BASE_URL 대체 검사가 무의미해진다. 그 신호를 잡는다.
grep -q 'callback-url:.*PUBLIC_OAUTH_CALLBACK_URL.*APP_BASE_URL' "$APP_YML" \
    && pass "콜백 URL 이 APP_BASE_URL 에서 파생된다(매핑 표 전제)" \
    || fail "콜백 URL 파생 관계가 바뀌었다 — 매핑 표의 '-' 항목을 다시 볼 것" \
            "$(grep -n 'callback-url' "$APP_YML")"

# ---- 새 검사가 실제로 배포 경로에 연결돼 있는가 ----

# 함수만 있고 아무도 부르지 않으면 이 모든 것이 장식이다.
grep -q "ongo_invalid_startup_gate_env_vars" "$DEPLOY_SH" \
    && pass "deploy.sh 가 기동 게이트 검사를 호출한다" \
    || fail "deploy.sh 가 기동 게이트 검사를 부르지 않는다 — 검사가 죽어 있다" ""

# preflight_env 안에서 불려야 **서비스 중지 전**에 걸린다. 밖이면 무중단 실패가 깨진다.
sed -n '/^preflight_env()/,/^}/p' "$DEPLOY_SH" | grep -q "ongo_invalid_startup_gate_env_vars" \
    && pass "기동 게이트 검사가 preflight_env 안에서 불린다(무중단 실패 보장)" \
    || fail "검사가 preflight_env 밖에 있다 — 중지 뒤에 걸리면 서비스가 내려간 채 남는다" ""

echo ""
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
