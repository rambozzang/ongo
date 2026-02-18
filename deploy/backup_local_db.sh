#!/bin/bash
# Backup Local Docker Database
# Usage: ./backup_local_db.sh

CONTAINER_NAME=ongo-postgres
DB_USER=ongo_user
DB_NAME=ongo_db
OUTPUT_FILE=ongo_backup_$(date +%Y%m%d).sql

echo ">>> Dumping database from Docker container '$CONTAINER_NAME'..."

docker exec -t $CONTAINER_NAME pg_dump -U $DB_USER -d $DB_NAME --clean --if-exists > $OUTPUT_FILE

if [ $? -eq 0 ]; then
    echo ">>> Backup successful: $OUTPUT_FILE"
else
    echo ">>> Backup failed!"
    exit 1
fi
