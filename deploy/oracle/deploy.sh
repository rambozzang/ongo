#!/bin/bash
set -e

# Deployment Script for Oracle Cloud (Run on Server)
# Pre-requisite: git clone repo to ~/app/ongo

PROJECT_ROOT=~/app/ongo
DEPLOY_DIR=/opt/ongo
BACKEND_DIR=$DEPLOY_DIR/backend
FRONTEND_DIR=/var/www/ongo

echo ">>> 1. Updating Code..."
cd $PROJECT_ROOT
git pull origin main

echo ">>> 2. Building Backend..."
cd $PROJECT_ROOT/backend
chmod +x gradlew
./gradlew bootJar -x test

echo ">>> 3. Deploying Backend..."
# Create deployment directory if not exists
sudo mkdir -p $BACKEND_DIR
# Backup existing jar
if [ -f "$BACKEND_DIR/ongo-api.jar" ]; then
    sudo mv $BACKEND_DIR/ongo-api.jar $BACKEND_DIR/ongo-api.jar.bak
fi
# Copy new jar
sudo cp onGo-api/build/libs/onGo-api-*.jar $BACKEND_DIR/ongo-api.jar
sudo chown root:root $BACKEND_DIR/ongo-api.jar

echo ">>> 4. Building Frontend..."
cd $PROJECT_ROOT/frontend
npm ci
npm run build

echo ">>> 5. Deploying Frontend..."
sudo mkdir -p $FRONTEND_DIR
# Clean old files (optional: keep backup?)
sudo rm -rf $FRONTEND_DIR/*
sudo cp -r dist/* $FRONTEND_DIR/
sudo chown -R nginx:nginx $FRONTEND_DIR
sudo chmod -R 755 $FRONTEND_DIR

echo ">>> 6. Restarting Services..."
sudo systemctl daemon-reload
sudo systemctl restart ongo-backend
sudo systemctl reload nginx

echo ">>> Deployment Complete!"
