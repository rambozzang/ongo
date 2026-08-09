# 실계정 멀티플랫폼 게시 스모크 테스트

MockWebServer 계약 테스트는 외부 요청 형식을 검증하고, 이 스모크 테스트는 실제 연결된 테스트 채널에서 최종 게시 상태와 게시 URL을 검증합니다. 운영 데이터와 토큰을 저장소에 넣지 않습니다.

## 안전 규칙

- 별도 테스트 계정을 사용합니다.
- 기본값은 모든 플랫폼 `PRIVATE` 게시입니다.
- 테스트 영상은 짧은 샘플 영상 하나만 사용합니다.
- `ONGO_ALLOW_PUBLIC_SMOKE=true`는 승인된 공개 테스트에서만 사용합니다.
- `ONGO_BEARER_TOKEN`과 `ONGO_METADATA_JSON`은 셸 히스토리나 CI 로그에 출력하지 않습니다.

## 실행

```bash
export ONGO_BASE_URL=https://ongo.example.com
export ONGO_BEARER_TOKEN='운영 테스트 계정 JWT'
export ONGO_VIDEO_FILE=/secure/path/smoke.mp4
export ONGO_EXPECTED_PLATFORMS=YOUTUBE,INSTAGRAM,TIKTOK,THREADS
export ONGO_METADATA_JSON='{
  "title": "ongo private provider smoke",
  "description": "automated private smoke test",
  "tags": ["ongo-smoke"],
  "category": "Entertainment",
  "platforms": [
    {"platform":"YOUTUBE","title":"ongo smoke","description":"private","tags":["ongo-smoke"],"visibility":"PRIVATE"},
    {"platform":"INSTAGRAM","title":"ongo smoke","description":"private","tags":["ongo-smoke"],"visibility":"PRIVATE"},
    {"platform":"TIKTOK","title":"ongo smoke","tags":["ongo-smoke"],"visibility":"PRIVATE"},
    {"platform":"THREADS","title":"ongo smoke","tags":["ongo-smoke"],"visibility":"PRIVATE"}
  ]
}'

./scripts/provider-smoke.sh
```

플랫폼별로 연결된 계정이 여러 개면 요청의 각 항목에 `channelId`를 명시합니다. 스크립트는 `202 Accepted`만으로 성공 처리하지 않으며, 요청 payload에 기대한 플랫폼이 모두 들어 있는지 먼저 확인한 뒤 `/api/v1/videos/{id}`를 조회합니다. 기대한 모든 플랫폼이 `PUBLISHED`이고 비어 있지 않은 `http(s)` URL을 반환하면 각 URL에 실제 HTTP 요청을 보내 2xx/3xx 응답까지 확인해야 성공합니다. URL 확인 제한시간은 `ONGO_URL_CHECK_TIMEOUT_SECONDS`(기본 20초)로 조정할 수 있습니다. `FAILED`, `REJECTED`, `UNCONFIRMED`가 하나라도 발생하거나 게시 링크가 응답하지 않으면 즉시 실패합니다.

## 증거 보관

성공한 실행마다 실행 시각, 배포 커밋 SHA, 기대 플랫폼, 각 플랫폼의 최종 URL을 운영 기록에 남깁니다. 액세스 토큰, 응답 헤더, 개인정보가 포함된 원문 응답은 보관하지 않습니다.

로컬 Docker가 필요한 Testcontainers 통합 테스트와 달리 이 검사는 실제 배포 URL에서 실행합니다. 따라서 배포 전에는 MockWebServer/단위 테스트를, 출시 승인 시에는 이 스모크 테스트를 각각 통과해야 합니다.
