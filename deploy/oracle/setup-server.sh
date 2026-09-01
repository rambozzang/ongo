#!/bin/bash
set -e

# The direct-install deployment scripts use /data/ongo as their single runtime
# root. Keep first-time provisioning on the same path; otherwise this script
# can read a password from /opt while deploy.sh/start.sh later read /data and
# fail with a misleading missing-credential error.
ENV_FILE="${ONGO_ENV_FILE:-/data/ongo/.env}"
if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

DB_USERNAME="${DB_USERNAME:-ongo}"
DB_NAME="${DB_NAME:-ongo}"

# OracleCloud(Oracle Linux 9 / ARM) Setup Script
# Run as root (sudo bash setup-server.sh)

if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: setup-server.sh must run as root (sudo bash deploy/oracle/setup-server.sh)." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEMD_UNIT_SOURCE="$SCRIPT_DIR/../ongo-backend.service"
SYSTEMD_UNIT_TARGET="/etc/systemd/system/ongo-backend.service"

echo ">>> System Update..."
dnf update -y

echo ">>> Installing dependencies..."
# dnf install -y git curl wget unzip nano

# 1. Java 25 (Pre-installed check)
echo ">>> Checking Java 25..."
if java -version 2>&1 | grep -q "25."; then
    echo "Java 25 is already installed."
else
    echo "Warning: Java 25 not found or version mismatch."
    # dnf install -y java-25-openjdk-devel
fi

# 2. Node.js (Pre-installed check)
echo ">>> Checking Node.js..."
if node -v 2>&1 | grep -q "v"; then
    echo "Node.js is installed: $(node -v)"
else
    echo "Warning: Node.js not found."
    # dnf module enable nodejs:20 -y && dnf install -y nodejs
fi

# 3. PostgreSQL 16 Configuration (Service setup)
echo ">>> Configuring PostgreSQL 16..."
# Assuming service is named postgresql-16 or postgresql
if systemctl list-units --type=service | grep -q "postgres"; then
    echo "Postgres service found."
    # service_name=$(systemctl list-units --type=service --all | grep postgres | head -n 1 | awk '{print $1}')
    # systemctl enable --now $service_name
    
    # Configure the role/database from the deployment secret; never embed a password here.
    echo "Creating DB User and Database if not exist..."
    : "${DB_PASSWORD:?DB_PASSWORD must be set in ${ENV_FILE} (or ONGO_ENV_FILE)}"
    sudo -u postgres psql -v ON_ERROR_STOP=1 \
        --set=db_user="$DB_USERNAME" \
        --set=db_password="$DB_PASSWORD" \
        --set=db_name="$DB_NAME" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'db_user')\gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'db_user', :'db_password')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db_name')\gexec
SQL
    
    # Allow external access
    # 1. Listen on all interfaces
    # sudo sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" /var/lib/pgsql/16/data/postgresql.conf
    # 2. Allow remote connections in pg_hba.conf (e.g., host all all 0.0.0.0/0 scram-sha-256)
    # This requires manual verification or careful editing.
    
    # Check/Update pg_hba.conf if needed (manual verification recommended for pre-installed env)
    echo "Note: Please verify pg_hba.conf allows md5/scram-sha-256 auth for remote connections."
else
    echo "Warning: PostgreSQL service not found running."
fi

# 4. Backend systemd service
#
# 배포 호스트에서 start.sh 를 수동 실행하면 JVM이 systemd 감시 밖에 남는다. 실제 운영에서
# 재부팅 뒤 복구가 보장되지 않았던 원인이므로, 초기 설정 단계에서 저장소의 유닛을 설치하고
# enable만 한다. start는 하지 않는다 — 아직 .env/DB/외부 자격증명을 검증하지 않은 상태에서
# 서비스를 자동 기동하면 설정 오류를 숨긴 채 재시작 루프에 들어갈 수 있다.
echo ">>> Installing onGo backend systemd unit..."
if [ ! -f "$SYSTEMD_UNIT_SOURCE" ]; then
    echo "ERROR: systemd unit source not found: $SYSTEMD_UNIT_SOURCE" >&2
    exit 1
fi
if ! getent passwd jenkins >/dev/null 2>&1; then
    echo "ERROR: required service user 'jenkins' does not exist. Create the deployment user before setup." >&2
    exit 1
fi
install -o root -g root -m 0644 "$SYSTEMD_UNIT_SOURCE" "$SYSTEMD_UNIT_TARGET"
systemctl daemon-reload
systemctl enable ongo-backend.service
echo "Backend unit installed and enabled (not started). Run deploy/deploy.sh after credentials and schema checks."

# 5. Nginx Configuration
echo ">>> Configuring Nginx..."
if command -v nginx &> /dev/null; then
    echo "Nginx is installed."
    systemctl enable --now nginx
else
    echo "Warning: Nginx not found."
fi

# 6. Firewall Configuration
echo ">>> Configuring Firewall..."
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https
# Backend and PostgreSQL stay bound to localhost; only Nginx is internet-facing.
firewall-cmd --reload

echo ">>> Setup Complete!"
echo "Next steps: deploy/deploy.sh 스크립트로 배포하세요."
