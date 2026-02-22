#!/bin/bash
# OnGo Database Restore Script (To Oracle Cloud)
# Usage: ./restore_remote_db.sh <backup_file.sql>

set -e

# Configuration
TARGET_HOST="ongo.codelabtiger.com"
TARGET_PORT="54332"
TARGET_DB="ongo"
TARGET_USER="ongo"
# Password for remote DB
TARGET_PASSWORD="aaaAAA111!!!"

if [ -z "$1" ]; then
    echo "Usage: $0 <backup_file.sql>"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Error: File '$BACKUP_FILE' not found."
    exit 1
fi

echo "============================================"
echo "  OnGo DB Restore (To Oracle Cloud)"
echo "============================================"
echo "Source File: $BACKUP_FILE"
echo "Target: $TARGET_HOST:$TARGET_PORT / DB: $TARGET_DB"
echo "User: $TARGET_USER"
echo "--------------------------------------------"

echo "⚠️  WARNING: This will overwrite data in the target database '$TARGET_DB'!"
read -p "Are you sure? (y/N): " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "Restoring..."

# Using docker to run psql client (using postgres:16-alpine to match server version)
docker run -i --rm \
    -e PGPASSWORD="$TARGET_PASSWORD" \
    postgres:16-alpine \
    psql -h "$TARGET_HOST" -p "$TARGET_PORT" -U "$TARGET_USER" -d "$TARGET_DB" < "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Restore completed successfully!"
else
    echo ""
    echo "❌ Restore failed! (Check if port 5432 is open on remote server)"
    exit 1
fi
