#!/bin/bash
# 백엔드 기동에 반드시 필요한 환경변수 목록 — deploy.sh 와 start.sh 가 공유한다.
#
# 이 파일은 실행되지 않고 source 된다.
#
# 왜 한 곳에 모으는가:
#   deploy.sh 와 start.sh 가 각자 목록을 하드코딩하면 드리프트가 난다. 한쪽에만 변수를 추가하면
#   선행 검증을 통과하고 기동 단계에서 죽는다. 그러면 서비스가 멈춘 채 다시 뜨지 않는다.
#
# 검증이 두 곳에서 도는 이유:
#   deploy.sh 는 stop.sh 로 서비스를 멈추기 **전에** 이 검증을 돌린다. 값이 없으면 배포만
#   실패하고 기존 프로세스는 그대로 살아 있다(무중단 실패).
#   start.sh 의 검증은 수동 기동이나 다른 경로로 들어올 때를 위한 방어선으로 남긴다.
#
# 비밀값 자체는 절대 출력하지 않는다. 누락된 **변수명만** 보고한다.

ONGO_REQUIRED_ENV_VARS="JWT_SECRET DB_PASSWORD PLATFORM_TOKEN_ENCRYPTION_KEY GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET KAKAO_CLIENT_ID KAKAO_CLIENT_SECRET OAUTH_STATE_SECRET APP_BASE_URL R2_ACCOUNT_ID R2_BUCKET R2_ACCESS_KEY R2_SECRET_KEY CORS_ALLOWED_ORIGINS PORTONE_STORE_ID PORTONE_CHANNEL_KEY PORTONE_API_SECRET"

# 누락된 변수명을 공백 구분 문자열로 표준출력에 낸다. 전부 있으면 빈 문자열.
ongo_missing_env_vars() {
    local missing=""
    local var
    for var in $ONGO_REQUIRED_ENV_VARS; do
        if [ -z "${!var:-}" ]; then
            missing="$missing $var"
        fi
    done
    echo "$missing"
}

# 누락 변수를 사람이 읽을 형태로 출력한다. 값은 찍지 않는다.
#   $1 = ongo_missing_env_vars 결과, $2 = .env 경로
ongo_report_missing_env_vars() {
    local missing="$1"
    local env_file="$2"
    local var

    echo "[ERROR] 다음 환경변수가 설정되지 않았습니다:"
    for var in $missing; do
        echo "          - $var"
    done
    echo "        ${env_file} 파일에 값을 넣은 뒤 다시 배포해주세요."

    # PORTONE_* 가 목록에 들어온 뒤에만 동작한다. 값 발급 경로를 바로 안내한다.
    case "$missing" in
        *PORTONE_*)
            echo ""
            echo "        PORTONE_* 값은 포트원 관리자 콘솔에서 발급합니다."
            echo "          STORE_ID       상점 정보"
            echo "          CHANNEL_KEY    결제 연동 > 연동 관리 > 채널"
            echo "          API_SECRET     결제 연동 > API Keys"
            echo "          WEBHOOK_SECRET 결제 연동 > 연동 관리 > 결제알림(Webhook) 관리"
            echo "                         > '웹훅 시크릿 발급'. API_SECRET 과 다른 값입니다."
            echo "        웹훅 URL 등록: https://ongo.codelabtiger.com/api/v1/portone/webhook"
            echo "        상세: docs/operations/EXTERNAL_SERVICE_SETUP_CHECKLIST.md 4.2절"
            ;;
    esac
}

# .env 를 로드한다. 이미 로드된 환경변수를 덮어쓴다.
#   $1 = .env 경로
#
# 반환값으로 실패를 구분한다. 호출자가 fail-closed 를 선택할 수 있어야 하기 때문이다.
# 로드 실패를 무시하면 위험하다. 부모 환경(예: Jenkins)에 같은 이름의 값이 들어 있으면
# .env 가 깨졌는데도 누락 목록이 비어 검증을 통과한다.
#
#   0  정상
#   1  파일 없음
#   2  source 실패 (문법 오류 등)
ongo_load_env_file() {
    local env_file="$1"
    local rc=0

    [ -f "$env_file" ] || return 1

    # Windows 줄바꿈이 섞이면 source 가 깨진다.
    # BSD sed(macOS)는 이 형식을 거부하지만 서버는 GNU sed 다. 실패해도 로드는 계속 시도한다.
    sed -i 's/\r$//' "$env_file" 2>/dev/null || true

    set -a
    # shellcheck disable=SC1090
    source "$env_file" || rc=2
    set +a

    # Older production .env files predate the explicit CORS setting. Derive a
    # same-origin allow-list from APP_BASE_URL so a routine deployment can
    # recover without weakening the production validator. An explicit
    # CORS_ALLOWED_ORIGINS value always wins and can contain multiple origins.
    if [ -z "${CORS_ALLOWED_ORIGINS:-}" ] && [ -n "${APP_BASE_URL:-}" ]; then
        export CORS_ALLOWED_ORIGINS="${APP_BASE_URL%/}"
    fi

    return $rc
}
