#!/bin/bash
# ============================================================
# onGo - 배포 스크립트
# 사용법: bash deploy.sh [all|backend|frontend] [--skip-git] [--skip-build]
# ============================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

# ============================================================
# 경로 설정
# ============================================================
BASE_DIR="/data/ongo"
SRC_DIR="$BASE_DIR/src"
JAR_DIR="$BASE_DIR/jar"
WWW_DIR="$BASE_DIR/www"
LOG_DIR="$BASE_DIR/log"
PID_FILE="$BASE_DIR/app.pid"
ENV_FILE="$BASE_DIR/.env"
DEPLOY_TARGET="${1:-all}"
SKIP_GIT=false
SKIP_BUILD=false

# Argument Parsing
for arg in "$@"; do
    case $arg in
        --skip-git)
            SKIP_GIT=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
    esac
done

TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "============================================"
echo "  onGo 배포 ($DEPLOY_TARGET)"
echo "  시각: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"
echo ""

# 디렉토리 존재 확인
if [ ! -d "$SRC_DIR" ]; then
    error "$SRC_DIR 디렉토리가 없습니다."
    exit 1
fi

cd "$SRC_DIR"

# ============================================================
# Git Pull
# ============================================================
if [ "$SKIP_GIT" = false ]; then
    step "최신 소스코드 가져오기 (git pull)..."
    git fetch --all
    BEFORE_HASH=$(git rev-parse HEAD)
    git pull origin main 2>/dev/null || git pull origin master
    AFTER_HASH=$(git rev-parse HEAD)

    if [ "$BEFORE_HASH" = "$AFTER_HASH" ] && [ "$DEPLOY_TARGET" = "all" ]; then
        warn "변경사항이 없습니다. 강제 배포하려면: bash deploy.sh backend"
    fi

    info "현재 커밋: $(git log --oneline -1)"
else
    info "Git Pull 건너뜀 (--skip-git)"
fi
echo ""

# ============================================================
# Backend 배포
# ============================================================
deploy_backend() {
    step "Backend 배포 시작..."

    cd "$SRC_DIR/backend"

    # Gradle 빌드 (테스트 스킵)
    if [ "$SKIP_BUILD" = false ]; then
        info "Gradle bootJar 빌드 중..."
        chmod +x gradlew
        ./gradlew :onGo-api:bootJar -x test --no-daemon 2>&1 | tail -5
    else
        info "Backend 빌드 건너뜀 (--skip-build)"
    fi

    # JAR 파일 복사
    mkdir -p "$JAR_DIR"
    JAR_FILE=$(ls -t onGo-api/build/libs/ongo-api-*.jar 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ]; then
        error "JAR 파일을 찾을 수 없습니다. 빌드 실패!"
        exit 1
    fi

    # 백업
    if [ -f "$JAR_DIR/ongo-api.jar" ]; then
        cp "$JAR_DIR/ongo-api.jar" "$JAR_DIR/ongo-api.jar.backup.$TIMESTAMP"
        info "기존 JAR 백업: ongo-api.jar.backup.$TIMESTAMP"
    fi

    cp "$JAR_FILE" "$JAR_DIR/ongo-api.jar"
    info "JAR 복사 완료: $JAR_FILE → $JAR_DIR/ongo-api.jar"

    # 서비스 재시작
    info "Backend 재시작..."
    bash "$SRC_DIR/deploy/stop.sh" || true
    bash "$SRC_DIR/deploy/start.sh"

    # 상태 확인
    if [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
        info "Backend 실행 중 (PID: $(cat $PID_FILE))"
    else
        error "Backend 시작 실패! 로그:"
        tail -20 "$LOG_DIR/backend.log"
        exit 1
    fi

    # 오래된 백업 정리 (최근 3개만 유지)
    ls -t "$JAR_DIR"/ongo-api.jar.backup.* 2>/dev/null | tail -n +4 | xargs rm -f 2>/dev/null || true

    echo ""
}

# ============================================================
# Frontend 배포
# ============================================================
deploy_frontend() {
    step "Frontend 배포 시작..."

    cd "$SRC_DIR/frontend"

    # 의존성 설치 + 빌드
    if [ "$SKIP_BUILD" = false ]; then
        info "npm 의존성 설치..."
        npm ci --silent 2>&1 | tail -3

        info "Vite 빌드 중..."
        VITE_API_URL=/api npm run build 2>&1 | tail -5
    else
        info "Frontend 빌드 건너뜀 (--skip-build)"
    fi

    if [ ! -d "dist" ]; then
        error "빌드 결과물(dist)이 없습니다. 빌드 실패!"
        exit 1
    fi

    # 백업
    if [ -d "$WWW_DIR" ]; then
        mv "$WWW_DIR" "$WWW_DIR.backup.$TIMESTAMP"
        info "기존 프론트엔드 백업: $WWW_DIR.backup.$TIMESTAMP"
    fi

    # 배포
    mkdir -p "$WWW_DIR"
    cp -r dist/* "$WWW_DIR/"

    # SELinux 컨텍스트 적용 (Nginx가 파일을 읽을 수 있도록)
    restorecon -Rv "$WWW_DIR" > /dev/null 2>&1 || true
    info "Frontend 배포 완료: $WWW_DIR"

    # Nginx 재로드
    sudo nginx -t && sudo systemctl reload nginx

    # 오래된 백업 정리 (최근 3개만 유지)
    ls -dt "$WWW_DIR".backup.* 2>/dev/null | tail -n +4 | xargs rm -rf 2>/dev/null || true

    echo ""
}

# ============================================================
# 배포 실행
# ============================================================
case "$DEPLOY_TARGET" in
    all)
        deploy_backend
        deploy_frontend
        ;;
    backend)
        deploy_backend
        ;;
    frontend)
        deploy_frontend
        ;;
    *)
        error "알 수 없는 타겟: $DEPLOY_TARGET"
        echo "사용법: bash deploy.sh [all|backend|frontend]"
        exit 1
        ;;
esac

# ============================================================
# 배포 결과 요약
# ============================================================
echo "============================================"
echo "  배포 완료!"
echo "============================================"
echo ""
echo "서비스 상태:"
if [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
    echo "  - Backend:  active (PID: $(cat $PID_FILE))"
else
    echo "  - Backend:  inactive"
fi
echo "  - Nginx:    $(systemctl is-active nginx 2>/dev/null || echo 'unknown')"
echo ""
echo "접속 URL:"
DOMAIN="ongo.codelabtiger.com"
echo "  - 웹 UI:    https://${DOMAIN}"
echo "  - API:      https://${DOMAIN}/api"
echo ""
echo "로그 확인:"
echo "  - Backend:  tail -f $LOG_DIR/backend.log"
echo "  - Nginx:    sudo tail -f /var/log/nginx/ongo-*.log"
echo ""
