#!/bin/bash
set -e

ENV_FILE="${ONGO_ENV_FILE:-/opt/ongo/.env}"
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
    : "${DB_PASSWORD:?DB_PASSWORD must be set in /opt/ongo/.env (or ONGO_ENV_FILE)}"
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

# 4. Nginx Configuration
echo ">>> Configuring Nginx..."
if command -v nginx &> /dev/null; then
    echo "Nginx is installed."
    systemctl enable --now nginx
else
    echo "Warning: Nginx not found."
fi

# 5. Firewall Configuration
echo ">>> Configuring Firewall..."
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https
# Backend and PostgreSQL stay bound to localhost; only Nginx is internet-facing.
firewall-cmd --reload

echo ">>> Setup Complete!"
echo "Next steps: deploy/deploy.sh 스크립트로 배포하세요."
