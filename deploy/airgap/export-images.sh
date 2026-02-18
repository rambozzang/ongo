#!/bin/bash
# Export Docker Images for Air-gapped Deployment
# Usage: ./export-images.sh

set -e

PROJECT_ROOT=~/work/app/ongo
OUTPUT_DIR=$PROJECT_ROOT/deploy/airgap/images
TIMESTAMP=$(date +"%Y%m%d")

echo ">>> 1. Creating output directory..."
mkdir -p $OUTPUT_DIR

echo ">>> 2. Building Images..."
echo "Building Backend..."
docker build -t ongo-backend:latest -f $PROJECT_ROOT/backend/Dockerfile $PROJECT_ROOT/backend
echo "Building Frontend..."
docker build -t ongo-frontend:latest -f $PROJECT_ROOT/frontend/Dockerfile $PROJECT_ROOT/frontend

echo ">>> 3. Pulling External Images..."
docker pull postgres:16
docker pull minio/minio:latest

echo ">>> 4. Saving Images to Tar..."
echo "Saving ongo-backend..."
docker save ongo-backend:latest -o $OUTPUT_DIR/ongo-backend.tar
echo "Saving ongo-frontend..."
docker save ongo-frontend:latest -o $OUTPUT_DIR/ongo-frontend.tar
echo "Saving postgres:16..."
docker save postgres:16 -o $OUTPUT_DIR/postgres.tar
echo "Saving minio/minio:latest..."
docker save minio/minio:latest -o $OUTPUT_DIR/minio.tar

echo ">>> 5. Packaging Deployment Bundle..."
cd $PROJECT_ROOT/deploy/airgap
tar -czvf ongo-deploy-bundle-$TIMESTAMP.tar.gz images/ docker-compose.prod.yml import-images.sh import-images.bat README.md

echo ">>> Export Complete!"
echo "Bundle created at: $PROJECT_ROOT/deploy/airgap/ongo-deploy-bundle-$TIMESTAMP.tar.gz"
