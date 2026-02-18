#!/bin/bash
# Import Docker Images in Air-gapped Environment
# Usage: ./import-images.sh

set -e

echo ">>> 1. Loading Docker Images..."
if [ -d "images" ]; then
    for img in images/*.tar; do
        echo "Loading $img..."
        docker load -i "$img"
    done
else
    echo "Error: 'images' directory not found. Please extract the bundle first."
    exit 1
fi

echo ">>> 2. Verifying Images..."
docker images | grep ongo

echo ">>> 3. Starting Services..."
docker-compose -f docker-compose.prod.yml up -d

echo ">>> Deployment Complete!"
echo "Access Frontend at http://localhost"
echo "Access MinIO Console at http://localhost:9001"
