#!/usr/bin/env bash
#
# nginx 요청 본문 상한이 **백엔드가 실제로 허용하는 크기와 같은지** 고정한다.
#
# ## 왜 필요한가
#
# nginx 기본 `client_max_body_size` 는 1MB 다. 명시하지 않으면 에셋 업로드
# (`POST /api/v1/assets`), 이미지 게시(`POST /api/v1/videos/{id}/images`), 쇼츠 시트·템플릿
# 업로드가 1MB 를 넘는 순간 **프록시에서 413 으로 잘린다.** 백엔드까지 닿지 않으므로 서버
# 로그에는 아무것도 남지 않고, 화면에는 원인을 알 수 없는 실패만 보인다.
#
# 백엔드는 2GB 를 허용한다고 두 곳에 적어 두었다(`FileValidationUtil.DEFAULT_MAX_FILE_SIZE`,
# `AssetController`). 그 선언과 프록시 설정이 어긋나 있으면 **선언한 적 없는 한도가 실제
# 한도가 된다** — 코드만 읽어서는 알 수 없는 종류의 불일치다.
#
# ## 왜 grep 하나로 끝내지 않는가
#
# 파일 어딘가에 그 줄이 있다는 사실은 **그 줄이 배포에 반영된다는 뜻이 아니다.**
#
#  - `location /api/` 블록 **안에** 있어야 API 프록시에 적용된다. 다른 블록에 있으면
#    파일에는 보이지만 업로드는 그대로 막힌다.
#  - `frontend/nginx.conf` 는 `frontend/Dockerfile` 이 이미지에 굽는 경우에만 쓰인다.
#    COPY 대상 경로가 바뀌면 편집한 파일이 이미지에 들어가지 않는다.
#  - `deploy/oracle/nginx-ongo.conf` 는 배포 가이드가 지시하는 파일이어야 운영자가
#    그것을 복사한다.
#
# 그래서 블록 범위·Dockerfile 배선·문서 지시를 함께 본다.
#
# 실행: bash deploy/nginx-body-size.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONTAINER_CONF="$REPO_ROOT/frontend/nginx.conf"
HOST_CONF="$REPO_ROOT/deploy/oracle/nginx-ongo.conf"
DOCKERFILE="$REPO_ROOT/frontend/Dockerfile"
DEPLOY_GUIDE="$REPO_ROOT/docs/DEPLOYMENT_GUIDE.md"

PASS=0
FAIL=0
pass() { PASS=$((PASS + 1)); echo "  ok   - $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL - $1"; [ $# -gt 1 ] && echo "         $2"; }

# `location <prefix> {` 부터 짝이 맞는 `}` 까지를 떼어 낸다.
#
# 중괄호 깊이를 세므로 블록 안에 중첩이 있어도 경계를 잃지 않는다. 단순히 다음 `}` 까지
# 자르면 중첩된 블록에서 잘못 끊긴다.
extract_location() {
    local file="$1" prefix="$2"
    awk -v want="$prefix" '
        $1 == "location" && $2 == want { inblock = 1; depth = 0 }
        inblock {
            print
            n = gsub(/{/, "{"); depth += n
            m = gsub(/}/, "}"); depth -= m
            if (depth <= 0 && n + m > 0 && NR > 0 && depth == 0 && seen) { inblock = 0 }
            if (n > 0) seen = 1
            if (seen && depth == 0) { inblock = 0; seen = 0 }
        }
    ' "$file"
}

echo "nginx 요청 본문 상한"

# ---- 백엔드 선언과 같은 값인가 ----

# 백엔드가 2GB 를 허용한다고 적어 둔 곳. 이 값이 바뀌면 프록시도 함께 바뀌어야 한다.
BACKEND_LIMIT_DECL="$REPO_ROOT/backend/onGo-common/src/main/kotlin/com/ongo/common/util/FileValidationUtil.kt"
grep -q "DEFAULT_MAX_FILE_SIZE: Long = 2L \* 1024 \* 1024 \* 1024" "$BACKEND_LIMIT_DECL" \
    && pass "백엔드 선언 상한이 2GB 다" \
    || fail "백엔드 상한이 2GB 가 아니다 — 프록시 값도 함께 맞춰야 한다" \
            "$(grep -n DEFAULT_MAX_FILE_SIZE "$BACKEND_LIMIT_DECL" | head -1)"

grep -q "2L \* 1024 \* 1024 \* 1024" "$REPO_ROOT/backend/onGo-api/src/main/kotlin/com/ongo/api/asset/AssetController.kt" \
    && pass "에셋 컨트롤러 상한이 2GB 다" \
    || fail "에셋 컨트롤러 상한이 2GB 가 아니다" ""

# ---- 두 설정 모두 API 블록 안에 값이 있는가 ----

for entry in "컨테이너 이미지:$CONTAINER_CONF" "Oracle 호스트:$HOST_CONF"; do
    label="${entry%%:*}"
    conf="${entry#*:}"

    if [ ! -f "$conf" ]; then
        fail "$label 설정 파일이 없다" "$conf"
        continue
    fi

    api_block="$(extract_location "$conf" "/api/")"
    if [ -z "$api_block" ]; then
        fail "$label 설정에 location /api/ 블록이 없다" "$conf"
        continue
    fi

    # **핵심.** 블록 밖에 있으면 파일에는 보여도 업로드는 그대로 막힌다.
    echo "$api_block" | grep -q "client_max_body_size 2g;" \
        && pass "$label: /api/ 블록 안에 2g 상한이 있다" \
        || fail "$label: /api/ 블록에 2g 상한이 없다 — 1MB 기본값으로 업로드가 413 된다" \
                "$(echo "$api_block" | head -5)"

    # 정적·웹소켓 경로에는 두지 않는다. 큰 본문을 받지 않는 곳까지 넓힐 이유가 없다.
    for other in "/" "/ws/" "/assets/"; do
        block="$(extract_location "$conf" "$other")"
        [ -z "$block" ] && continue
        echo "$block" | grep -q "client_max_body_size" \
            && fail "$label: $other 블록에 불필요한 본문 상한이 있다" "$(echo "$block" | head -3)" \
            || pass "$label: $other 블록은 본문 상한을 건드리지 않는다"
    done
done

# ---- 편집한 파일이 실제로 배포에 반영되는가 ----

# frontend/nginx.conf 는 이미지에 구워질 때만 쓰인다. COPY 가 사라지면 이 테스트가
# 지키는 파일과 실제로 뜨는 설정이 갈라진다.
grep -q "^COPY nginx.conf /etc/nginx/conf.d/default.conf" "$DOCKERFILE" \
    && pass "Dockerfile 이 frontend/nginx.conf 를 이미지 설정으로 굽는다" \
    || fail "Dockerfile 이 nginx.conf 를 굽지 않는다 — 편집한 파일이 이미지에 들어가지 않는다" \
            "$(grep -n nginx.conf "$DOCKERFILE")"

# 그 이미지가 실제로 배포 조합에 쓰이는지. 안 쓰이면 위 설정은 아무 데도 반영되지 않는다.
grep -q "ongo-frontend" "$REPO_ROOT/deploy/airgap/docker-compose.prod.yml" \
    && pass "배포 compose 가 프론트 이미지를 사용한다" \
    || fail "배포 compose 가 프론트 이미지를 쓰지 않는다" ""

# Oracle 호스트 설정은 운영자가 손으로 복사한다. 가이드가 그 파일을 지시해야
# 편집한 파일이 실제로 설치된다.
grep -q "deploy/oracle/nginx-ongo.conf" "$DEPLOY_GUIDE" \
    && pass "배포 가이드가 이 호스트 설정을 설치하도록 지시한다" \
    || fail "배포 가이드가 다른 파일을 설치한다 — 편집한 파일이 운영에 반영되지 않는다" \
            "$(grep -n nginx "$DEPLOY_GUIDE" | head -3)"

# 배포 스크립트는 nginx 를 설치하지 않는다. 그 사실을 명시적으로 고정해 둔다 —
# "고쳤으니 배포하면 적용된다"는 오해가 이 항목에서 가장 비싸다.
grep -qE "^[^#]*cp .*nginx-ongo.conf" "$REPO_ROOT/deploy/deploy.sh" \
    && fail "deploy.sh 가 nginx 설정을 설치한다 — 이 테스트의 전제가 바뀌었다" "" \
    || pass "deploy.sh 는 nginx 설정을 설치하지 않는다(운영자 수동 반영)"

echo ""
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
