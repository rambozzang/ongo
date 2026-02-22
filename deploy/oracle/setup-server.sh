#!/bin/bash
set -e

# OracleCloud(Oracle Linux 9 / ARM) Setup Script
# Run as root (sudo bash setup-server.sh)

echo ">>> System Update..."
dnf update -y

echo ">>> Installing dependencies..."
# dnf install -y git curl wget unzip nano

# 1. Java 21 (Pre-installed check)
echo ">>> Checking Java 21..."
if java -version 2>&1 | grep -q "21."; then
    echo "Java 21 is already installed."
else
    echo "Warning: Java 21 not found or version mismatch."
    # dnf install -y java-21-openjdk-devel
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
    
    # Configure Postgres user (Try to run safely)
    echo "Creating DB User and Database if not exist..."
    sudo -u postgres psql -c "CREATE USER ongo WITH PASSWORD 'aaaAAA111!!!';" || echo "User likely exists"
    sudo -u postgres psql -c "CREATE DATABASE ongo OWNER ongo;" || echo "DB likely exists"
    
    sudo -u postgres psql -c "CREATE DATABASE ongo OWNER ongo;" || echo "DB likely exists"
    
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
firewall-cmd --permanent --add-port=8070/tcp # Backend
firewall-cmd --permanent --add-port=5432/tcp # PostgreSQL (External Access)
firewall-cmd --reload

echo ">>> Setup Complete!"
echo "Next steps: deploy/deploy.sh 스크립트로 배포하세요."
