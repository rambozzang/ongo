#!/usr/bin/env bash

set -euo pipefail

MIGRATION_DIR="${1:-backend/onGo-api/src/main/resources/db/migration}"

if [[ ! -d "$MIGRATION_DIR" ]]; then
    echo "Flyway migration directory not found: $MIGRATION_DIR" >&2
    exit 1
fi

versions="$({
    find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -exec basename {} \;
} | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n)"

if [[ -z "$versions" ]]; then
    echo "No versioned Flyway migrations found in $MIGRATION_DIR" >&2
    exit 1
fi

duplicates="$(printf '%s\n' "$versions" | uniq -d)"
if [[ -n "$duplicates" ]]; then
    echo "Duplicate Flyway migration version(s) found in $MIGRATION_DIR:" >&2
    while IFS= read -r version; do
        find "$MIGRATION_DIR" -maxdepth 1 -type f -name "V${version}__*.sql" -exec basename {} \; | sort >&2
    done <<< "$duplicates"
    exit 1
fi

echo "Flyway migration versions are unique (${versions//$'\n'/, })."
