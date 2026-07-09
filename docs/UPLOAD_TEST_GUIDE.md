# 유튜브/틱톡 실제 업로드 테스트 가이드

## 1. 사전 준비

### 1.1 OAuth 앱 등록 및 토큰 발급

#### YouTube (Google)
1. https://console.cloud.google.com 접속
2. 새 프로젝트 생성
3. **API 및 서비스 > 라이브러리** → `YouTube Data API v3` 활성화
4. **API 및 서비스 > 사용자 인증 정보** → OAuth 2.0 클라이언트 ID 생성
   - 애플리케이션 유형: 웹 애플리케이션
   - 승인된 리디렉션 URI: `http://localhost:8070/api/v1/auth/callback/youtube` (프로젝트 콜백에 맞게 조정)
5. OAuth 동의 화면 설정 → 테스트 사용자 추가
6. `client_id`, `client_secret` 복사
7. 아래 URL로 브라우저 접속 후 인가 코드 발급:
   ```
   https://accounts.google.com/o/oauth2/v2/auth?
     client_id=YOUR_CLIENT_ID
     &redirect_uri=YOUR_REDIRECT_URI
     &response_type=code
     &scope=https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly
     &access_type=offline
     &prompt=consent
   ```
8. 발급된 code로 access token/refresh token 교환

#### TikTok
1. https://developers.tiktok.com 접속
2. 앱 등록 → Content Posting API 권한 신청
3. `client_key`, `client_secret` 복사
4. OAuth 흐름으로 access token/refresh token 발급

### 1.2 환경 변수 설정

`.env` 또는 `.envrc`에 다음을 추가 (절대 커밋 금지):

```bash
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export TIKTOK_CLIENT_KEY=your-tiktok-client-key
export TIKTOK_CLIENT_SECRET=your-tiktok-client-secret
```

## 2. 테스트 채널 DB 삽입

### 2.1 access token 암호화

`platform.token.encryption-key` 값을 확인 (기본값 `b25nby1kZXYtYWVzMjU2LWVuY3J5cHRpb24ta2V5MDA=`).

```bash
cd backend
./gradlew :onGo-infrastructure:run -Dexec.mainClass="com.ongo.testutil.TokenEncryptionUtilKt" \
  -Dexec.args="b25nby1kZXYtYWVzMjU2LWVuY3J5cHRpb24ta2V5MDA= YOUR_ACCESS_TOKEN"
```

출력된 암호문을 복사합니다.

### 2.2 channels 테이블에 삽입

```sql
-- user_id는 실제 존재하는 사용자 ID로 변경
INSERT INTO channels (
    user_id,
    platform,
    platform_channel_id,
    channel_name,
    channel_url,
    subscriber_count,
    access_token,
    refresh_token,
    token_expires_at,
    status
) VALUES (
    1,
    'YOUTUBE',
    'your-youtube-channel-id',
    'Test YouTube Channel',
    'https://www.youtube.com/channel/your-channel-id',
    0,
    'ENCRYPTED_ACCESS_TOKEN',
    'ENCRYPTED_REFRESH_TOKEN',
    NOW() + INTERVAL '1 hour',
    'ACTIVE'
);

INSERT INTO channels (
    user_id,
    platform,
    platform_channel_id,
    channel_name,
    channel_url,
    subscriber_count,
    access_token,
    refresh_token,
    token_expires_at,
    status
) VALUES (
    1,
    'TIKTOK',
    'your-tiktok-open-id',
    'Test TikTok Account',
    'https://www.tiktok.com/@your-username',
    0,
    'ENCRYPTED_ACCESS_TOKEN',
    'ENCRYPTED_REFRESH_TOKEN',
    NOW() + INTERVAL '1 hour',
    'ACTIVE'
);
```

## 3. API 호출

### 3.1 로그인 후 JWT 토큰 발급

```bash
curl -X POST http://localhost:8070/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"your-email@example.com","password":"your-password"}'
```

응답에서 `accessToken`을 복사합니다.

### 3.2 스트리밍 게시 API 호출

```bash
JWT="your-jwt-token"

METADATA='{
  "title": "Test Upload",
  "description": "This is a test upload",
  "tags": ["test", "ongo"],
  "category": "Entertainment",
  "thumbnailUrl": null,
  "platforms": [
    {
      "platform": "YOUTUBE",
      "title": "YouTube Test Upload",
      "description": "YouTube test description",
      "tags": ["test", "youtube"],
      "visibility": "PRIVATE",
      "scheduledAt": null
    },
    {
      "platform": "TIKTOK",
      "title": "TikTok Test Upload",
      "description": "TikTok test description",
      "tags": ["test", "tiktok"],
      "visibility": "PRIVATE",
      "scheduledAt": null
    }
  ]
}'

curl -X POST http://localhost:8070/api/v1/videos/stream-publish \
  -H "Authorization: Bearer $JWT" \
  -F "metadata=$METADATA" \
  -F "file=@/path/to/test-video.mp4"
```

## 4. 결과 확인

- API는 202 Accepted 즉시 반환
- 로그에서 업로드 진행/완료/실패 확인
- YouTube Studio / TikTok 앱에서 비공개 영상 확인

## 주의사항

- **반드시 테스트 계정/비공개(PRIVATE) 영상으로 테스트**
- access token은 짧은 만료 시간이므로 만료 시 refresh token으로 갱신
- TikTok은 Content Posting API 승인이 필요하며, 미승인 시 `initVideoUpload`에서 403 오류 발생 가능
- 대용량 파일은 2GB 메모리 버퍼링 제한 있음

