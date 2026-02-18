# OnGo Air-gapped Deployment Guide

This guide describes how to deploy the OnGo system in an offline (air-gapped) environment using Docker.

## Prerequisites

- A Linux machine (Physical or VM) provided in the secure environment.
- **Docker** and **Docker Compose** must be installed on the target machine.
  - *Note: If Docker is not installed, you must bring the Docker RPM/Deb packages and install them manually.*

## Installation Steps

1. **Transfer the Bundle**:
    - Copy the `ongo-deploy-bundle-YYYYMMDD.tar.gz` file to the target machine (via USB or secure file transfer).

2. **Extract the Bundle**:

    ```bash
    tar -xzvf ongo-deploy-bundle-YYYYMMDD.tar.gz
    cd deploy/airgap # or wherever it extracted
    ```

3. **Run Import Script**:
    - **Linux**:
        - Make sure scripts are executable:

            ```bash
            chmod +x import-images.sh
            ```

        - Run the import script:

            ```bash
            ./import-images.sh
            ```

    - **Windows (Command Prompt / PowerShell)**:

        ```cmd
        import-images.bat
        ```

    - This script will:
        - Load all Docker images (Backend, Frontend, Postgres, MinIO) from the `images/` folder.
        - Start the services using `docker-compose.prod.yml`.

4. **Verify Deployment**:
    - Check running containers:

        ```bash
        docker ps
        ```

    - Access the application:
        - **Frontend**: `http://localhost` (or server IP)
        - **Backend API**: `http://localhost:8090`
        - **MinIO Console**: `http://localhost:9001`

## Maintenance

- **Stop Services**:

    ```bash
    docker-compose -f docker-compose.prod.yml down
    ```

- **Restart Services**:

    ```bash
    docker-compose -f docker-compose.prod.yml restart
    ```

- **View Logs**:

    ```bash
    docker-compose -f docker-compose.prod.yml logs -f
    ```
