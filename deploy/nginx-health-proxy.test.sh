#!/usr/bin/env bash
#
# 백엔드 헬스 체크가 **밖에서 보이는지** 고정한다.
#
# ## 무엇이 깨져 있었나
#
# nginx 에 `/actuator/` 프록시 룰이 없어서 `/actuator/health` 요청이 `location /` 의
# `try_files $uri $uri/ /index.html` 로 떨어졌다. 그래서 **백엔드가 죽어 있어도**
# 헬스 조회가 프론트 HTML 과 함께 200 을 돌려줬다.
#
# 결과는 최악의 조합이다. 홈페이지는 열리고 헬스는 200 인데 `/api/` 는 전부 502 다.
# 밖에서 보면 정상이라, 실제로 운영 장애를 그 상태로 놓쳤다.
#
# ## 왜 정확 일치(`=`)여야 하는가
#
# `/actuator/` 전체를 열면 나중에 `management.endpoints.web.exposure.include` 가
# 넓어지는 날 내부 정보가 함께 나간다. 지금 백엔드는 `health` 하나만 노출하지만,
# 그 설정이 바뀌어도 프록시에서 막히도록 경로를 좁혀 둔다.
#
# 실행: bash deploy/nginx-health-proxy.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONTAINER_CONF="$REPO_ROOT/frontend/nginx.conf"
HOST_CONF="$REPO_ROOT/deploy/oracle/nginx-ongo.conf"
APP_YML="$REPO_ROOT/backend/onGo-api/src/main/resources/application.yml"
SECURITY_CONFIG="$REPO_ROOT/backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/security/SecurityConfig.kt"

PASS=0
FAIL=0
pass() { PASS=$((PASS + 1)); echo "  ok   - $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL - $1"; [ $# -gt 1 ] && echo "         $2"; }

echo "nginx 헬스 체크 프록시"

# ---- 두 설정 모두 health 를 프록시하는가 ----

for entry in "컨테이너 이미지:$CONTAINER_CONF" "Oracle 호스트:$HOST_CONF"; do
    label="${entry%%:*}"
    conf="${entry#*:}"

    if [ ! -f "$conf" ]; then
        fail "$label 설정 파일이 없다" "$conf"
        continue
    fi

    # **핵심.** 이 룰이 없으면 백엔드가 죽어도 헬스가 200 을 돌려준다.
    grep -qE "^[[:space:]]*location[[:space:]]*=[[:space:]]*/actuator/health[[:space:]]*\{" "$conf" \
        && pass "$label: /actuator/health 를 정확 일치로 프록시한다" \
        || fail "$label: /actuator/health 프록시가 없다 — 백엔드가 죽어도 프론트 HTML 로 200 이 나간다" \
                "$(grep -n 'location' "$conf" | head -5)"

    # 그 블록이 실제로 백엔드로 넘기는가. location 만 있고 proxy_pass 가 없으면 무의미하다.
    grep -qE "proxy_pass[[:space:]]+http://[^/]+/actuator/health" "$conf" \
        && pass "$label: 백엔드 8070 으로 넘긴다" \
        || fail "$label: proxy_pass 가 /actuator/health 로 가지 않는다" ""

    # **경로를 넓히지 않는다.** prefix 로 열면 exposure 설정이 바뀌는 날 함께 샌다.
    grep -qE "^[[:space:]]*location[[:space:]]+/actuator/[[:space:]]*\{" "$conf" \
        && fail "$label: /actuator/ 전체가 열려 있다 — health 만 열어야 한다" \
                "$(grep -n '/actuator/' "$conf")" \
        || pass "$label: /actuator/ 전체를 열지 않는다"
done

# ---- 백엔드가 실제로 그 경로를 열어 두는가 ----

# 프록시만 뚫고 백엔드가 막으면 헬스는 401 이 된다. 두 쪽이 함께 맞아야 의미가 있다.
grep -q '"/actuator/health"' "$SECURITY_CONFIG" \
    && pass "백엔드가 /actuator/health 를 인증 없이 허용한다" \
    || fail "백엔드가 /actuator/health 를 막는다 — 프록시만 뚫으면 401 이 나간다" \
            "$(grep -n actuator "$SECURITY_CONFIG")"

# 노출 범위가 넓어지면 위 프록시 경로도 다시 검토해야 한다. 그 신호를 여기서 잡는다.
grep -qE "^[[:space:]]*include:[[:space:]]*health[[:space:]]*$" "$APP_YML" \
    && pass "actuator 노출이 health 하나로 제한돼 있다" \
    || fail "actuator 노출 범위가 바뀌었다 — 프록시 경로를 다시 검토할 것" \
            "$(grep -n -A 4 'exposure:' "$APP_YML")"

# 익명 호출자에게 내부 구성을 보여주지 않는지. 여기가 풀리면 헬스가 정보 노출 통로가 된다.
grep -qE "show-details:[[:space:]]*when-authorized" "$APP_YML" \
    && pass "익명 헬스 응답에 상세 정보를 담지 않는다" \
    || fail "health show-details 가 열려 있다 — 익명에게 내부 구성이 나간다" \
            "$(grep -n 'show-details' "$APP_YML")"

echo ""
echo "tests=$((PASS + FAIL)) passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
