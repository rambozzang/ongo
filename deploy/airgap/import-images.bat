@echo off
REM Import Docker Images in Air-gapped Environment (Windows)
REM Usage: import-images.bat

echo ">>> 1. Loading Docker Images..."
if exist "images" (
    for %%f in (images\*.tar) do (
        echo Loading %%f...
        docker load -i "%%f"
    )
) else (
    echo "Error: 'images' directory not found. Please extract the bundle first."
    pause
    exit /b 1
)

echo ">>> 2. Verifying Images..."
docker images | findstr ongo

echo ">>> 3. Starting Services..."
docker-compose -f docker-compose.prod.yml up -d

echo ">>> Deployment Complete!"
echo "Access Frontend at http://localhost"
echo "Access MinIO Console at http://localhost:9001"
pause
