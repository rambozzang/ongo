#!/usr/bin/env bash
#
# 준비 상태 보고서가 **앱과 같은 판정을 하는지** 고정한다.
#
# ## 왜 이 테스트가 필요한가
#
# 보고서가 "YouTube 연동 가능" 이라고 했는데 앱이 "설정되지 않았습니다" 를 띄우면,
# 그 보고서는 없느니만 못하다. 운영자는 보고서를 믿고 배포하고, 사용자는 채널 연결
# 화면에서 막힌다. 밖에서는 장애로 보이지 않는다.
#
# 그래서 여기서는 문구가 아니라 **판정 규칙과 변수 매핑이 Kotlin 소스와 같은지**를 본다.
# 목록을 이 파일에 복사해 두지 않고 소스에서 직접 읽어 대조한다.
#
# 실행: bash deploy/readiness-report.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REPORT="$REPO_ROOT/deploy/readiness-report.sh"
ADAPTER="$REPO_ROOT/backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/PlatformConfigurationAdapter.kt"
APP_YML="$REPO_ROOT/backend/onGo-api/src/main/resources/application.yml"

PASS=0
FAIL=0
pass() { PASS=$((PASS + 1)); echo "  ok   - $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL - $1"; [ $# -gt 1 ] && [ -n "$2" ] && echo "         $2"; }

echo "준비 상태 보고서"

# ---- 판정 규칙이 앱과 같은가 ----

APP_MIN="$(grep -o 'MIN_CREDENTIAL_LENGTH = [0-9][0-9]*' "$ADAPTER" | grep -o '[0-9][0-9]*' | head -1)"
REPORT_MIN="$(grep -o 'MIN_CREDENTIAL_LENGTH=[0-9][0-9]*' "$REPORT" | grep -o '[0-9][0-9]*' | head -1)"

if [ -n "$APP_MIN" ] && [ -n "$REPORT_MIN" ]; then
    [ "$APP_MIN" = "$REPORT_MIN" ] \
        && pass "최소 길이 기준이 앱과 같다 (${APP_MIN}자)" \
        || fail "최소 길이가 다르다 — 앱 ${APP_MIN} vs 보고서 ${REPORT_MIN}" \
                "보고서가 앱과 다른 판정을 내린다"
else
    fail "최소 길이를 양쪽에서 읽지 못했다" "앱=${APP_MIN:-없음} 보고서=${REPORT_MIN:-없음}"
fi

# placeholder 목록이 갈라지면 한쪽만 통과시키는 값이 생긴다.
MISSING_TOKENS=""
for token in dummy placeholder change-me your- localhost; do
    grep -q "\"$token\"" "$ADAPTER" || continue   # 앱에 없는 토큰은 볼 필요 없다
    grep -q "\*$token\*" "$REPORT" || MISSING_TOKENS="$MISSING_TOKENS $token"
done
[ -z "$MISSING_TOKENS" ] \
    && pass "placeholder 거부 목록이 앱과 같다" \
    || fail "보고서가 거르지 않는 placeholder 가 있다:$MISSING_TOKENS" \
            "앱은 막는데 보고서는 '설정됨' 이라고 말한다"

# ---- 플랫폼 매핑이 앱과 같은가 ----

# 앱이 자격증명으로 판정하는 플랫폼(= status(platform, a, b) 형태로 부르는 것)만 뽑는다.
# NAVER_CLIP 처럼 configured=false 로 못 박는 것은 제외된다.
APP_PLATFORMS="$(grep -oE 'Platform\.[A-Z_]+ -> status\(' "$ADAPTER" \
    | sed 's/Platform\.//; s/ -> status(//' | sort -u)"

APP_COUNT="$(printf '%s\n' "$APP_PLATFORMS" | grep -c . || true)"
[ "$APP_COUNT" -gt 0 ] \
    && pass "앱에서 자격증명 판정 대상 ${APP_COUNT}개를 읽었다" \
    || fail "앱의 플랫폼 판정 목록을 읽지 못했다 — 형식이 바뀌었다" ""

# 보고서의 check_platform 라벨 → 대문자 enum 이름으로 맞춰 비교한다.
report_has_platform() {
    case "$1" in
        YOUTUBE)     grep -q 'check_platform "YouTube"' "$REPORT" ;;
        TIKTOK)      grep -q 'check_platform "TikTok"' "$REPORT" ;;
        INSTAGRAM)   grep -q 'check_platform "Instagram"' "$REPORT" ;;
        FACEBOOK)    grep -q 'check_platform "Facebook"' "$REPORT" ;;
        THREADS)     grep -q 'check_platform "Threads"' "$REPORT" ;;
        PINTEREST)   grep -q 'check_platform "Pinterest"' "$REPORT" ;;
        LINKEDIN)    grep -q 'check_platform "LinkedIn"' "$REPORT" ;;
        WORDPRESS)   grep -q 'check_platform "WordPress"' "$REPORT" ;;
        TUMBLR)      grep -q 'check_platform "Tumblr"' "$REPORT" ;;
        VIMEO)       grep -q 'check_platform "Vimeo"' "$REPORT" ;;
        DAILYMOTION) grep -q 'check_platform "Dailymotion"' "$REPORT" ;;
        # X 는 자격증명이 있어도 게시 경로가 없어 별도 줄로 안내한다.
        TWITTER)     grep -q 'X(Twitter)' "$REPORT" ;;
        *)           return 1 ;;
    esac
}

UNCOVERED=""
while IFS= read -r platform; do
    [ -n "$platform" ] || continue
    report_has_platform "$platform" || UNCOVERED="$UNCOVERED $platform"
done <<< "$APP_PLATFORMS"

[ -z "$UNCOVERED" ] \
    && pass "앱이 판정하는 모든 플랫폼이 보고서에 있다" \
    || fail "보고서가 빠뜨린 플랫폼이 있다:$UNCOVERED" \
            "그 플랫폼은 미설정인데도 보고서에 나타나지 않는다"

# ---- 환경변수 이름이 application.yml 과 같은가 ----
#
# 이름이 어긋나면 보고서는 늘 "없음" 이라고 말한다. 운영자는 있는 키를 다시 발급받는다.

check_env_name() {
    local prop="$1" expected="$2"
    local actual
    actual="$(grep -A1 "^  ${prop%%.*}:" "$APP_YML" >/dev/null 2>&1; \
        grep -oE "\\\$\{${expected}:" "$APP_YML" | head -1)"
    if [ -n "$actual" ]; then
        grep -q "\b$expected\b" "$REPORT" \
            && return 0 \
            || { echo "$expected"; return 1; }
    fi
    return 0
}

WRONG_NAMES=""
for pair in \
    "GOOGLE_CLIENT_ID" "GOOGLE_CLIENT_SECRET" \
    "TIKTOK_CLIENT_KEY" "TIKTOK_CLIENT_SECRET" \
    "INSTAGRAM_APP_ID" "INSTAGRAM_APP_SECRET" \
    "FACEBOOK_APP_ID" "FACEBOOK_APP_SECRET" \
    "THREADS_APP_ID" "THREADS_APP_SECRET" \
    "PINTEREST_APP_ID" "PINTEREST_APP_SECRET" \
    "LINKEDIN_CLIENT_ID" "LINKEDIN_CLIENT_SECRET" \
    "WORDPRESS_CLIENT_ID" "WORDPRESS_CLIENT_SECRET" \
    "TUMBLR_CONSUMER_KEY" "TUMBLR_CONSUMER_SECRET" \
    "VIMEO_CLIENT_ID" "VIMEO_CLIENT_SECRET" \
    "DAILYMOTION_API_KEY" "DAILYMOTION_API_SECRET"
do
    # application.yml 이 실제로 이 이름을 쓰는지 먼저 확인한다.
    grep -q "\${$pair:" "$APP_YML" || { WRONG_NAMES="$WRONG_NAMES $pair(yml에없음)"; continue; }
    grep -q "\b$pair\b" "$REPORT" || WRONG_NAMES="$WRONG_NAMES $pair(보고서에없음)"
done

[ -z "$WRONG_NAMES" ] \
    && pass "플랫폼 환경변수 이름 22개가 application.yml 과 일치한다" \
    || fail "환경변수 이름이 어긋났다:$WRONG_NAMES" \
            "보고서가 늘 '없음' 이라고 말해 운영자가 있는 키를 다시 발급받는다"

# ---- 필수 목록을 복사해 두지 않는가 ----

# 필수 변수는 required-env.sh 에서 읽어야 한다. 복사해 두면 드리프트가 난다.
grep -q 'for var in \$ONGO_REQUIRED_ENV_VARS' "$REPORT" \
    && pass "기동 필수 목록을 required-env.sh 에서 읽는다" \
    || fail "필수 목록을 보고서가 따로 갖고 있다 — 드리프트가 난다" ""

grep -q 'ongo_invalid_startup_gate_env_vars' "$REPORT" \
    && pass "기동 검증기의 추가 규칙(길이·https)도 확인한다" \
    || fail "값이 있어도 기동에서 죽는 경우를 보고하지 않는다" ""

# ---- 비밀값을 출력하지 않는가 ----

# `${!var}` 를 그대로 echo/printf 하는 줄이 있으면 비밀값이 화면과 로그에 남는다.
if grep -nE '(echo|printf).*\$\{![A-Za-z_]' "$REPORT" >/dev/null 2>&1; then
    fail "비밀값을 출력하는 줄이 있다" "$(grep -nE '(echo|printf).*\$\{![A-Za-z_]' "$REPORT" | head -3)"
else
    pass "비밀값을 출력하지 않는다(변수명과 상태만 낸다)"
fi

# ---- 실제로 도는가 ----

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 필수값이 빠진 .env → rc=1 이어야 한다.
cat > "$TMP/empty.env" <<'ENVEOF'
JWT_SECRET=
ENVEOF
ENV_FILE="$TMP/empty.env" bash "$REPORT" >"$TMP/out1.log" 2>&1
rc=$?
[ "$rc" -eq 1 ] \
    && pass "필수값이 없으면 rc=1 로 기동 불가를 알린다" \
    || fail "필수값이 없는데 rc=$rc 로 끝났다" "$(tail -3 "$TMP/out1.log")"

# .env 가 없으면 rc=2 (점검 불가). 0 으로 끝나면 "문제 없음" 으로 오인된다.
ENV_FILE="$TMP/nope.env" bash "$REPORT" >"$TMP/out2.log" 2>&1
rc=$?
[ "$rc" -eq 2 ] \
    && pass ".env 가 없으면 rc=2 로 점검 불가를 알린다" \
    || fail ".env 가 없는데 rc=$rc 로 끝났다" "$(tail -3 "$TMP/out2.log")"

# 필수값이 모두 있으면 rc=0. 플랫폼이 하나도 없어도 기동은 가능하므로 0 이다.
{
    for var in $(source "$REPO_ROOT/deploy/required-env.sh"; echo "$ONGO_REQUIRED_ENV_VARS"); do
        echo "$var=valid-production-value-1234"
    done
    echo "APP_BASE_URL=https://ongo.example.com"
    echo "OAUTH_STATE_SECRET=$(printf 'o%.0s' $(seq 1 40))"
} > "$TMP/full.env"

ENV_FILE="$TMP/full.env" bash "$REPORT" >"$TMP/out3.log" 2>&1
rc=$?
[ "$rc" -eq 0 ] \
    && pass "필수값이 모두 있으면 rc=0 이다" \
    || fail "정상인데 rc=$rc 로 끝났다" "$(tail -5 "$TMP/out3.log")"

# 아래는 처음에 "플랫폼 0 개면 경고한다" 로 썼다가 고친 자리다.
#
# GOOGLE_CLIENT_ID/SECRET 은 **로그인 때문에 기동 필수값**이고, YouTube 는 같은
# 자격증명을 쓴다. 그래서 "기동 가능한데 플랫폼 0 개" 라는 상태는 존재할 수 없다 —
# 그 경고는 도달하지 않는 분기였다. 이 테스트가 그것을 드러냈다.
grep -qE "YouTube.*연동 가능" "$TMP/out3.log" \
    && pass "필수값만 있어도 YouTube 는 준비됨이다(로그인과 자격증명 공유)" \
    || fail "Google 자격증명이 있는데 YouTube 가 미설정으로 나온다" "$(grep -i youtube "$TMP/out3.log")"

grep -q "YouTube 외에 연동 가능한 플랫폼이 없습니다" "$TMP/out3.log" \
    && pass "YouTube 뿐이면 그 사실을 경고한다" \
    || fail "다른 플랫폼이 하나도 없는데 조용히 통과했다" "$(tail -6 "$TMP/out3.log")"

# 키가 있다는 것과 그 키에 권한이 붙어 있다는 것은 다르다. 보고서가 그 한계를
# 말하지 않으면 운영자는 "준비됨" 을 "게시된다" 로 읽는다.
grep -q "권한(스코프)" "$TMP/out3.log" \
    && pass "스코프·심사는 판정 대상이 아님을 밝힌다" \
    || fail "보고서가 자기 한계를 말하지 않는다" "$(tail -4 "$TMP/out3.log")"

# 플랫폼 키를 넣으면 준비됨으로 바뀌는가 — 판정이 실제로 값을 보는지 확인한다.
cp "$TMP/full.env" "$TMP/yt.env"
{
    echo "GOOGLE_CLIENT_ID=real-google-client-id-value"
    echo "GOOGLE_CLIENT_SECRET=real-google-client-secret-value"
} >> "$TMP/yt.env"
ENV_FILE="$TMP/yt.env" bash "$REPORT" >"$TMP/out4.log" 2>&1
grep -qE "YouTube.*연동 가능" "$TMP/out4.log" \
    && pass "자격증명을 넣으면 연동 가능으로 바뀐다" \
    || fail "값을 넣었는데 여전히 미설정으로 본다" "$(grep -i youtube "$TMP/out4.log")"

# placeholder 는 거부해야 한다. 앱이 막는 값을 보고서가 통과시키면 안 된다.
cp "$TMP/full.env" "$TMP/ph.env"
{
    echo "GOOGLE_CLIENT_ID=your-client-id-here"
    echo "GOOGLE_CLIENT_SECRET=your-client-secret-here"
} >> "$TMP/ph.env"
ENV_FILE="$TMP/ph.env" bash "$REPORT" >"$TMP/out5.log" 2>&1
grep -qE "YouTube.*연동 가능" "$TMP/out5.log" \
    && fail "placeholder 값을 설정됨으로 판정했다" "$(grep -i youtube "$TMP/out5.log")" \
    || pass "placeholder 값은 미설정으로 본다(앱과 같은 판정)"

echo ""
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
