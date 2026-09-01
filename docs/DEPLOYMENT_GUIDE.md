# OnGo Deployment Guide

This document describes how to deploy the OnGo application in two scenarios:

1. **Oracle Cloud (Online)**: Direct installation on Linux (No Docker).
2. **Private Network (Air-gapped)**: Offline deployment using Docker images.

---

## 1. Oracle Cloud Deployment (Direct)

**Target Environment**: Oracle Linux 9 (ARM/x86), JDK 21, Node.js 20, Postgres 16, Nginx installed.

### 1.1 Initial Setup

1. **Clone Repository**:

    ```bash
    sudo mkdir -p /data/ongo
    sudo git clone <repository_url> /data/ongo/src
    cd /data/ongo/src
    ```

2. **Environment Configuration**:
    - Copy the production environment template:

        ```bash
        sudo mkdir -p /data/ongo
        sudo cp deploy/oracle/.env.production.example /data/ongo/.env
        sudo nano /data/ongo/.env
        # Edit DB credentials, JWT_SECRET, etc.
        ```

3. **Run Setup Script**:
    - Configures the host firewall and creates the PostgreSQL role/database when PostgreSQL is already installed.
    - Direct Oracle deployment uses Cloudflare R2 for object storage; MinIO is only used by the air-gapped Docker profile below.
    - Installs and enables `ongo-backend.service` for reboot/crash recovery. It intentionally does not start the backend before credentials and schema checks pass.

        ```bash
        sudo bash deploy/oracle/setup-server.sh
        ```

### 1.2 Service Configuration

1. **Systemd Services**:

    `setup-server.sh` installs and enables this unit automatically. On an already-provisioned host, or when verifying the installation manually:

    ```bash
    sudo cp deploy/ongo-backend.service /etc/systemd/system/
    sudo systemctl daemon-reload
    sudo systemctl enable ongo-backend.service
    sudo systemctl is-enabled ongo-backend.service
    ```

    Do not run `java -jar` or `deploy/start.sh` separately after the unit is installed; deployment must restart the unit so the JVM remains in systemd's cgroup.

    `deploy.sh` refuses to replace the JAR when the unit is missing. This protects the running service from an unmanaged deployment; only an explicit emergency override (`ALLOW_UNMANAGED_BACKEND=true`) bypasses the gate and it does not provide reboot recovery.

    If an older Jenkins/manual JVM is still running during the first systemd deployment, the unit's managed-start mode stops that exact `ongo-api.jar` process and starts a new one inside systemd. The deploy health gate then requires the new PID and an `UP` response.

2. **Nginx Configuration**:

    ```bash
    sudo cp deploy/oracle/nginx-ongo.conf /etc/nginx/conf.d/ongo.conf
    # Remove default config if present
    # sudo rm /etc/nginx/conf.d/default.conf
    sudo nginx -t
    sudo systemctl reload nginx
    ```

### 1.3 Application Deployment

1. **Run Deploy Script**:
    - Builds Backend (Gradle) and Frontend (NPM).
    - Copies the backend JAR to `/data/ongo/jar` and the frontend to `/data/ongo/www`.
    - Applies the database migration/schema gate before restarting the backend.
    - Declares success only after a new backend PID is running and
      `/actuator/health` reports `UP`; a PID file alone is not sufficient.

        ```bash
        chmod +x deploy/deploy.sh
        ./deploy/deploy.sh all
        ```

---

## 2. Private Network Deployment (Air-gapped)

**Target Environment**: Offline Linux machine with Docker & Docker Compose installed.

### 2.1 Prepare Deployment Bundle (Online PC)

1. Run the export script on a machine with Internet & Docker:

    ```bash
    cd deploy/airgap
    chmod +x export-images.sh
    ./export-images.sh
    ```

2. This creates `ongo-deploy-bundle-YYYYMMDD.tar.gz`. Copy this file to the private network machine (via USB/Secure Copy).

### 2.2 Deploy (Offline PC)

1. **Extract Bundle**:
    - **Linux**: `tar -xzvf ongo-deploy-bundle-*.tar.gz`
    - **Windows**: Use 7-Zip or similar to extract the `.tar.gz` file.

2. **Import & Start**:
    - **Linux**:

        ```bash
        chmod +x import-images.sh
        ./import-images.sh
        ```

    - **Windows**:
        Double-click `import-images.bat` or run in Command Prompt:

        ```cmd
        cd deploy\airgap
        import-images.bat
        ```

3. **Access**:
    - Web: `http://localhost`
    - MinIO: `http://localhost:9001`

### 2.3 Troubleshooting

- **DB Connection**: Check `docker logs ongo-postgres`.
- **Backend Startup**: Check `docker logs ongo-backend`.

---

## 3. Database Migration

If you need to move your data from the local development/Docker environment to the Oracle Cloud production database:

### 3.1 Backup Local DB

Run the backup script on your local machine (where Docker is running):

```bash
chmod +x deploy/backup_local_db.sh
./deploy/backup_local_db.sh
```

This will create a file like `ongo_backup_20240218.sql`.

### 3.2 Restore into Production

Run the restore script on your local machine to upload and restore to Oracle Cloud:

```bash
chmod +x deploy/restore_remote_db.sh
./deploy/restore_remote_db.sh ongo_backup_20240218.sql
```

*Note: This script connects directly to the remote PostgreSQL port (54332). Ensure the port is open in the Oracle Cloud Security List and OS Firewall.*
