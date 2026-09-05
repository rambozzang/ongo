#!/usr/bin/env bash
#
# 앱이 들을 포트와 nginx 가 넘길 포트가 **어긋나지 않는지** 고정한다.
#
# ## 왜 이 조합이 위험한가
#
# 포트가 어긋나면 앱은 멀쩡히 뜨고 systemd 는 `active (running)` 인데 nginx 만
# 502 를 낸다. 프로세스도 살아 있고 앱 로그도 깨끗해서, 장애 원인 중 가장
# 찾기 어려운 형태다. 밖에서는 "서버가 죽었다" 로 보이지만 안에서는 아무 문제가
# 없어 보인다.
#
# ## 값이 세 곳에 있다
#
#  - `application.yml` 의 `server.port` — 기본값
#  - `start.sh` 의 `SERVER_PORT:-` 폴백 — 기동 확인용 health 호출에 쓴다
#  - nginx 의 `proxy_pass` — 실제 트래픽이 가는 곳
#
# 셋이 같아야 한다. 하나만 바뀌면 조용히 갈라진다.
#
# 런타임의 `.env` `SERVER_PORT` 오버라이드는 `deploy.sh` 의 `preflight_server_port`
# 가 배포 전에 막는다(서비스 중지 전이라 무중단 실패). 여기서는 **저장소 안의
# 기본값들이 서로 맞는지**만 본다.
#
# 실행: bash deploy/server-port-consistency.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

APP_YML="$REPO_ROOT/backend/onGo-api/src/main/resources/application.yml"
START_SH="$REPO_ROOT/deploy/start.sh"
HOST_CONF="$REPO_ROOT/deploy/oracle/nginx-ongo.conf"
DEPLOY_SH="$REPO_ROOT/deploy/deploy.sh"

PASS=0
FAIL=0
pass() { PASS=$((PASS + 1)); echo "  ok   - $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL - $1"; [ $# -gt 1 ] && echo "         $2"; }

echo "서버 포트 정합성"

# ---- 세 곳의 값을 각각 뽑는다 ----

# BSD/GNU sed 차이를 타지 않도록 `grep -o` 로 뽑는다. `\+` 는 BSD sed 에서 안 먹는다.
APP_PORT="$(sed -n '/^server:/,/^[^ ]/p' "$APP_YML" | grep -o 'port:[[:space:]]*[0-9][0-9]*' | grep -o '[0-9][0-9]*' | head -1)"
START_PORT="$(grep -o 'SERVER_PORT:-[0-9][0-9]*' "$START_SH" | grep -o '[0-9][0-9]*' | head -1)"
NGINX_PORT="$(grep -o 'proxy_pass http://localhost:[0-9][0-9]*' "$HOST_CONF" | grep -o '[0-9][0-9]*' | head -1)"

# 값을 못 뽑으면 아래 비교가 공허하게 통과한다. 먼저 막는다.
[ -n "$APP_PORT" ]   && pass "application.yml 에서 server.port 를 읽었다 ($APP_PORT)" \
                     || fail "application.yml 에서 server.port 를 읽지 못했다 — 형식이 바뀌었다" ""
[ -n "$START_PORT" ] && pass "start.sh 에서 SERVER_PORT 폴백을 읽었다 ($START_PORT)" \
                     || fail "start.sh 에서 SERVER_PORT 폴백을 읽지 못했다" ""
[ -n "$NGINX_PORT" ] && pass "nginx 에서 proxy_pass 포트를 읽었다 ($NGINX_PORT)" \
                     || fail "nginx 에서 proxy_pass 포트를 읽지 못했다" ""

# ---- 셋이 같은가 ----

# **핵심.** 여기가 어긋나면 systemd 는 정상인데 외부만 502 다.
if [ -n "$APP_PORT" ] && [ -n "$NGINX_PORT" ]; then
    [ "$APP_PORT" = "$NGINX_PORT" ] \
        && pass "앱 기본 포트와 nginx upstream 이 같다" \
        || fail "앱($APP_PORT) 과 nginx($NGINX_PORT) 포트가 다르다 — 앱은 뜨는데 외부는 전부 502 가 된다" ""
fi

# start.sh 폴백이 어긋나면 기동 확인용 health 호출이 엉뚱한 포트를 두드려,
# 정상 기동한 앱을 실패로 판정하고 90초 뒤 죽인다.
if [ -n "$APP_PORT" ] && [ -n "$START_PORT" ]; then
    [ "$APP_PORT" = "$START_PORT" ] \
        && pass "start.sh 폴백이 앱 기본 포트와 같다" \
        || fail "start.sh($START_PORT) 와 앱($APP_PORT) 포트가 다르다 — 정상 기동을 실패로 판정한다" ""
fi

# ---- 런타임 오버라이드를 배포가 막는가 ----

# 저장소 기본값이 맞아도 `.env` 한 줄로 깨질 수 있다. 그 방어선이 살아 있는지 본다.
grep -q "preflight_server_port" "$DEPLOY_SH" \
    && pass "deploy.sh 가 SERVER_PORT 오버라이드를 검사한다" \
    || fail "deploy.sh 에 포트 정합성 검사가 없다 — .env 한 줄로 전체 502 가 가능하다" ""

# 검사가 `preflight_env` 안에서 불려야 무중단 실패가 된다. `preflight_env` 자체가
# 서비스 중지·JAR 발행보다 먼저 도는 것은 deploy.test.sh 가 이미 고정하고 있으므로,
# 여기서는 **그 안에 묶여 있는지**만 본다.
sed -n '/^preflight_env()/,/^}/p' "$DEPLOY_SH" | grep -q "preflight_server_port" \
    && pass "포트 검사가 preflight_env 안에서 불린다(무중단 실패 보장)" \
    || fail "포트 검사가 preflight_env 밖에 있다 — 중지 뒤에 걸리면 서비스가 내려간 채 남는다" ""

# **SERVER_PORT 를 필수 목록에 넣지 않는다.** 비어 있는 것이 정상 동작이며,
# 필수로 만들면 기존 .env 를 쓰는 배포가 전부 막힌다.
grep -q "SERVER_PORT" "$REPO_ROOT/deploy/required-env.sh" \
    && fail "SERVER_PORT 가 필수 목록에 들어갔다 — 비어 있는 것이 정상이다" "" \
    || pass "SERVER_PORT 를 필수 목록에 넣지 않는다"

echo ""
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
