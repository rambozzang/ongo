#!/usr/bin/env bash
set -Eeuo pipefail

# Safe, repeatable production smoke test for the real provider publishing path.
# The caller supplies a PRIVATE test payload and a small test video. The script
# succeeds only when every expected upload reaches PUBLISHED with a real URL.

: "${ONGO_BASE_URL:?ONGO_BASE_URL is required (for example https://ongo.example.com)}"
: "${ONGO_BEARER_TOKEN:?ONGO_BEARER_TOKEN is required}"
: "${ONGO_VIDEO_FILE:?ONGO_VIDEO_FILE is required}"
: "${ONGO_METADATA_JSON:?ONGO_METADATA_JSON is required}"
: "${ONGO_EXPECTED_PLATFORMS:?ONGO_EXPECTED_PLATFORMS is required (comma-separated, e.g. YOUTUBE,INSTAGRAM)}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi
if [[ ! -s "$ONGO_VIDEO_FILE" ]]; then
  echo "test video does not exist or is empty: $ONGO_VIDEO_FILE" >&2
  exit 1
fi

base_url="${ONGO_BASE_URL%/}"
expected_platforms="$(printf '%s' "$ONGO_EXPECTED_PLATFORMS" | tr '[:lower:]' '[:upper:]' | tr -d ' ')"
request_json="$(mktemp -t ongo-provider-smoke-request.XXXXXX.json)"
response_body="$(mktemp -t ongo-provider-smoke-response.XXXXXX.json)"
detail_body="$(mktemp -t ongo-provider-smoke-detail.XXXXXX.json)"
trap 'rm -f "$request_json" "$response_body" "$detail_body"' EXIT

printf '%s' "$ONGO_METADATA_JSON" | jq -e '.title and (.platforms | type == "array" and length > 0)' >/dev/null

# A smoke test must not accidentally publish publicly. Set
# ONGO_ALLOW_PUBLIC_SMOKE=true only for an explicitly approved test run.
if [[ "${ONGO_ALLOW_PUBLIC_SMOKE:-false}" != "true" ]]; then
  printf '%s' "$ONGO_METADATA_JSON" | jq -e 'all(.platforms[]; (.visibility // "PRIVATE") == "PRIVATE")' >/dev/null \
    || { echo "smoke tests must use PRIVATE visibility" >&2; exit 1; }
fi

printf '%s' "$ONGO_METADATA_JSON" >"$request_json"

echo "Submitting provider smoke upload: $expected_platforms"
http_code="$({
  curl --fail-with-body --silent --show-error \
    --request POST "$base_url/api/v1/videos/stream-publish" \
    --header "Authorization: Bearer $ONGO_BEARER_TOKEN" \
    --form "metadata=<$request_json;type=application/json" \
    --form "file=@$ONGO_VIDEO_FILE" \
    --output "$response_body" \
    --write-out '%{http_code}'
} || true)"

if [[ "$http_code" != "202" ]]; then
  echo "stream publish did not return 202 (got $http_code)" >&2
  cat "$response_body" >&2
  exit 1
fi

video_id="$(jq -er '.data.videoId // .data.id' "$response_body")"
echo "Accepted as video $video_id; waiting for terminal provider states"

timeout_seconds="${ONGO_SMOKE_TIMEOUT_SECONDS:-900}"
poll_seconds="${ONGO_SMOKE_POLL_SECONDS:-15}"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  detail_code="$({
    curl --silent --show-error --output "$detail_body" --write-out '%{http_code}' \
      --header "Authorization: Bearer $ONGO_BEARER_TOKEN" \
      "$base_url/api/v1/videos/$video_id"
  } || true)"
  if [[ "$detail_code" != "200" ]]; then
    echo "video detail request failed (got $detail_code)" >&2
    cat "$detail_body" >&2
    exit 1
  fi

  if jq -e --arg expected "$expected_platforms" '
    ($expected | split(",")) as $platforms |
    ([.data.uploads[]? | {platform, status, platformUrl}] | map(select(.platform as $p | $platforms | index($p)))) as $uploads |
    ($uploads | length) == ($platforms | length) and
    all($uploads[]; .status == "PUBLISHED" and (.platformUrl | type == "string" and test("^https?://[^[:space:]]+$")))
  ' "$detail_body" >/dev/null; then
    echo "Provider smoke passed"
    jq -r '.data.uploads[] | "\(.platform): \(.status) \(.platformUrl // "")"' "$detail_body"
    exit 0
  fi

  if jq -e '
    ([.data.uploads[]?.status] | any(. == "FAILED" or . == "REJECTED" or . == "UNCONFIRMED" or . == "PARTIALLY_PUBLISHED"))
  ' "$detail_body" >/dev/null; then
    echo "Provider smoke reached a terminal failure" >&2
    jq -r '.data.uploads[] | "\(.platform): \(.status) \(.errorMessage // "") \(.platformUrl // "")"' "$detail_body" >&2
    exit 1
  fi

  sleep "$poll_seconds"
done

echo "Provider smoke timed out after ${timeout_seconds}s" >&2
jq -r '.data.uploads[]? | "\(.platform): \(.status) \(.errorMessage // "") \(.platformUrl // "")"' "$detail_body" >&2
exit 1
