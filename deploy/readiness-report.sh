#!/usr/bin/env bash
#
# 지금 이 배포가 **무엇을 할 수 있고 무엇을 못 하는지** 보고한다. 읽기 전용.
#
# ## 왜 필요한가
#
# `EXTERNAL_SERVICE_SETUP_CHECKLIST.md` 는 "무엇을 발급받아야 하는가" 를 알려준다.
# 그런데 발급을 받고 나면 반대쪽 질문이 남는다 — **"지금 뭐가 들어 있고 뭐가 비었나?"**
#
# 그 답이 없으면 이렇게 된다. 앱은 정상 기동하고, 화면도 열리고, 로그도 깨끗하다.
# 그런데 사용자가 채널 연결을 누르면 "이 플랫폼은 설정되지 않았습니다" 가 뜬다.
# 밖에서는 장애로 보이지 않고, 안에서는 아무 문제가 없어 보인다.
#
# 이 스크립트는 그 상태를 **배포 전에** 한 화면으로 보여준다.
#
# ## 앱과 같은 기준을 쓴다
#
# 판정 규칙을 새로 만들지 않는다. `PlatformConfigurationAdapter.isConfiguredValue` 와
# 같다 — 8자 미만이거나 dummy/placeholder/change-me/your-/localhost 가 섞이면
# 미설정으로 본다. 규칙이 갈라지면 이 보고서가 거짓말을 하게 된다.
#
# 그 일치는 `deploy/readiness-report.test.sh` 가 Kotlin 소스를 직접 읽어 고정한다.
#
# ## 비밀값은 절대 출력하지 않는다
#
# 변수명과 "있음/없음" 만 낸다. 길이도 찍지 않는다 — 길이만으로도 어떤 키인지
# 좁혀지는 경우가 있다.
#
# 실행:
#   bash deploy/readiness-report.sh                 # /data/ongo/.env 를 읽는다
#   ENV_FILE=/path/to/.env bash deploy/readiness-report.sh
#
# 종료 코드:
#   0  기동 가능 (플랫폼이 하나도 없어도 0 이다 — 기동을 막는 것은 아니므로)
#   1  기동 불가 (필수값 누락)
#   2  점검 불가 (.env 를 읽지 못함)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; DIM='\033[2m'; NC='\033[0m'

ENV_FILE="${ENV_FILE:-/data/ongo/.env}"

# 앱과 같은 값. PlatformConfigurationAdapter.MIN_CREDENTIAL_LENGTH 및
# ProductionConfigurationValidator.MIN_REAL_VALUE_LENGTH 와 일치해야 한다.
MIN_CREDENTIAL_LENGTH=8

# ---- 값 판정 (앱의 isConfiguredValue 와 같은 규칙) ----

is_real_value() {
    local value="$1"
    local normalized
    normalized="$(printf '%s' "$value" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"

    [ "${#normalized}" -ge "$MIN_CREDENTIAL_LENGTH" ] || return 1

    case "$normalized" in
        *dummy*|*placeholder*|*change-me*|*your-*|*localhost*) return 1 ;;
    esac
    return 0
}

# ---- .env 로드 ----

if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}[ERROR]${NC} .env 파일이 없습니다: $ENV_FILE"
    echo "        ENV_FILE=/경로/.env 로 지정할 수 있습니다."
    exit 2
fi

# 서브셸이 아니라 현재 셸에 올린다. 이 스크립트는 읽기 전용이고 종료하면 사라진다.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE" 2>/dev/null || {
    echo -e "${RED}[ERROR]${NC} .env 를 읽지 못했습니다(문법 오류): $ENV_FILE"
    exit 2
}
set +a

echo ""
echo -e "onGo 배포 준비 상태  ${DIM}($ENV_FILE)${NC}"
echo "════════════════════════════════════════════════════════"

# ---- 1) 기동 필수값 ----
#
# 하나라도 없으면 `ProductionConfigurationValidator` 가 기동을 막고, nginx 는 502 를 낸다.
# 목록의 근거는 deploy/required-env.sh 이며, 둘의 일치는
# deploy/startup-gate-parity.test.sh 가 고정한다.

echo ""
echo "▌1. 기동 필수 — 없으면 앱이 뜨지 않습니다"
echo ""

# shellcheck source=deploy/required-env.sh
if ! source "$SCRIPT_DIR/required-env.sh" 2>/dev/null; then
    echo -e "${RED}[ERROR]${NC} required-env.sh 를 읽지 못했습니다."
    exit 2
fi

BLOCKERS=0
for var in $ONGO_REQUIRED_ENV_VARS; do
    value="${!var:-}"
    if [ -z "$value" ]; then
        printf "  ${RED}✗${NC} %-28s ${RED}없음${NC}\n" "$var"
        BLOCKERS=$((BLOCKERS + 1))
    elif ! is_real_value "$value"; then
        printf "  ${RED}✗${NC} %-28s ${RED}값이 부적절${NC} ${DIM}(8자 미만이거나 placeholder)${NC}\n" "$var"
        BLOCKERS=$((BLOCKERS + 1))
    else
        printf "  ${GREEN}✓${NC} %-28s 설정됨\n" "$var"
    fi
done

# 기동 검증기의 추가 규칙. 값이 있어도 이걸 어기면 기동에서 죽는다.
GATE_ISSUES="$(ongo_invalid_startup_gate_env_vars)"
if [ -n "$GATE_ISSUES" ]; then
    for var in $GATE_ISSUES; do
        printf "  ${RED}✗${NC} %-28s ${RED}기동 검증 위반${NC} ${DIM}(길이 또는 https 규칙)${NC}\n" "$var"
        BLOCKERS=$((BLOCKERS + 1))
    done
fi

# ---- 2) 플랫폼별 연동 가능 여부 ----
#
# 여기가 비어도 앱은 뜬다. 다만 그 플랫폼은 채널 연결 화면에서 선택이 막힌다
# (StreamPublishUseCase 의 capability 게이트가 fail-closed 로 판정한다).

echo ""
echo "▌2. 플랫폼 연동 — 없으면 그 플랫폼만 선택 불가 (기동은 됩니다)"
echo ""

READY_PLATFORMS=0
TOTAL_PLATFORMS=0

check_platform() {
    local label="$1"; shift
    TOTAL_PLATFORMS=$((TOTAL_PLATFORMS + 1))

    local missing=""
    local var
    for var in "$@"; do
        is_real_value "${!var:-}" || missing="$missing $var"
    done

    if [ -z "$missing" ]; then
        printf "  ${GREEN}✓${NC} %-14s 연동 가능\n" "$label"
        READY_PLATFORMS=$((READY_PLATFORMS + 1))
    else
        printf "  ${YELLOW}−${NC} %-14s ${DIM}필요:%s${NC}\n" "$label" "$missing"
    fi
}

# 매핑 근거: PlatformConfigurationAdapter 의 status(...) 인자와
# application.yml 의 platform.* 블록.
#
# **YouTube 는 로그인과 같은 Google 자격증명을 쓴다.** GOOGLE_CLIENT_ID/SECRET 은
# 기동 필수값이므로, 앱이 뜬다면 YouTube 는 언제나 "연동 가능" 으로 나온다.
#
# 다만 그것은 **키가 있다**는 뜻일 뿐, 그 키에 YouTube 권한이 붙어 있다는 뜻은 아니다.
# Google Cloud 콘솔에서 YouTube Data API v3 를 활성화하고 OAuth 동의 화면에
# `youtube.upload`·`youtube.readonly` 스코프를 넣어야 실제 게시가 된다.
# 그 부분은 외부 호출 없이는 확인할 수 없으므로 여기서 판정하지 않는다.
check_platform "YouTube"     GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET
check_platform "TikTok"      TIKTOK_CLIENT_KEY TIKTOK_CLIENT_SECRET
check_platform "Instagram"   INSTAGRAM_APP_ID INSTAGRAM_APP_SECRET
check_platform "Facebook"    FACEBOOK_APP_ID FACEBOOK_APP_SECRET
check_platform "Threads"     THREADS_APP_ID THREADS_APP_SECRET
check_platform "Pinterest"   PINTEREST_APP_ID PINTEREST_APP_SECRET
check_platform "LinkedIn"    LINKEDIN_CLIENT_ID LINKEDIN_CLIENT_SECRET
check_platform "WordPress"   WORDPRESS_CLIENT_ID WORDPRESS_CLIENT_SECRET
check_platform "Tumblr"      TUMBLR_CONSUMER_KEY TUMBLR_CONSUMER_SECRET
check_platform "Vimeo"       VIMEO_CLIENT_ID VIMEO_CLIENT_SECRET
check_platform "Dailymotion" DAILYMOTION_API_KEY DAILYMOTION_API_SECRET

# X(Twitter)와 네이버 클립은 자격증명과 무관하게 게시 경로가 없다.
# PlatformUploadCapability 에서 supportsUpload=false 이며, Naver Clip 은
# PlatformConfigurationAdapter 가 아예 configured=false 로 못 박는다.
echo ""
printf "  ${DIM}·  X(Twitter)     자격증명과 무관하게 게시 미지원 (조회만)${NC}\n"
printf "  ${DIM}·  네이버 클립     공개 업로드·분석 API 없음${NC}\n"

# ---- 3) 기능별 선택 설정 ----

echo ""
echo "▌3. 기능별 — 없으면 그 기능만 동작하지 않습니다"
echo ""

check_optional() {
    local label="$1"; local note="$2"; shift 2
    local missing=""
    local var
    for var in "$@"; do
        is_real_value "${!var:-}" || missing="$missing $var"
    done
    if [ -z "$missing" ]; then
        printf "  ${GREEN}✓${NC} %s — 사용 가능\n" "$label"
    else
        printf "  ${YELLOW}−${NC} %s — ${DIM}%s (필요:%s)${NC}\n" "$label" "$note" "$missing"
    fi
}

check_optional "쇼츠 렌더링"   "서버 렌더 불가"        FFMPEG_PATH
check_optional "URL 임포트"    "외부 URL 가져오기 불가" YT_DLP_PATH
# AI 채팅은 DashScope 의 저가 모델(Qwen·MiniMax)만 쓴다(AiProvider.OFFERED). 크레딧 1개의 원가 예산 안에서
# 쓸 만한 답을 내는 모델이 그것뿐이라 Claude·Gemini 키가 있어도 채팅에는 쓰지 않는다.
# 이 키가 없으면 비싼 모델로 새지 않고 **AI 기능 전부가 멈춘다** — 원가가 조용히 5배가 되는 것보다 낫다.
check_optional "AI 채팅(DashScope)" "AI 기능 전부 불가"    DASHSCOPE_API_KEY

# ---- 요약 ----

echo ""
echo "════════════════════════════════════════════════════════"
if [ "$BLOCKERS" -gt 0 ]; then
    echo -e "${RED}기동 불가${NC} — 필수값 ${BLOCKERS}건이 빠졌습니다. 배포해도 502 가 납니다."
    echo -e "${DIM}발급 절차: docs/operations/EXTERNAL_SERVICE_SETUP_CHECKLIST.md${NC}"
    echo ""
    exit 1
fi

echo -e "${GREEN}기동 가능${NC} — 필수값이 모두 있습니다."
printf "플랫폼 연동: ${GREEN}%d${NC} / %d 준비됨\n" "$READY_PLATFORMS" "$TOTAL_PLATFORMS"

# YouTube 는 로그인과 자격증명을 공유하므로 기동 가능하면 항상 1 이상이다.
# 그래서 "0 개" 경고는 두지 않는다 — 도달하지 않는 분기는 읽는 사람을 헷갈리게 한다.
if [ "$READY_PLATFORMS" -le 1 ]; then
    echo ""
    echo -e "${YELLOW}주의:${NC} YouTube 외에 연동 가능한 플랫폼이 없습니다."
    echo "      사용자는 다른 플랫폼 채널을 연결할 수 없습니다(선택 자체가 막힙니다)."
fi

echo ""
echo -e "${DIM}이 보고서는 '키가 있는가' 만 봅니다. 그 키에 필요한 권한(스코프)이"
echo -e "붙어 있는지, 앱 심사가 통과했는지는 실제 연동을 해봐야 알 수 있습니다.${NC}"
echo ""
exit 0
