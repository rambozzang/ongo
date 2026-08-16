# onGo Backend 환경변수 정의서

## 개요

onGo 백엔드 애플리케이션에서 사용하는 환경변수 목록입니다.
`필수`로 표시된 항목은 프로덕션 환경에서 반드시 설정해야 하며, 나머지는 기본값이 제공됩니다.

---

## 1. 데이터베이스 (PostgreSQL)

### Spring Boot (application.yml)

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `DB_USERNAME` | PostgreSQL 접속 사용자명 | `ongo_user` | 선택 |
| `DB_PASSWORD` | PostgreSQL 접속 비밀번호 | dev: `ongo_password`, prod: 없음 | **필수 (prod)** |

### Docker Compose

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `POSTGRES_USER` | PostgreSQL 컨테이너 사용자명 | `ongo_user` | 선택 |
| `POSTGRES_PASSWORD` | PostgreSQL 컨테이너 비밀번호 | `ongo_password` | 선택 |
| `POSTGRES_DB` | PostgreSQL 데이터베이스명 | `ongo_db` | 선택 |

---

## 2. 인증 / 보안

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `JWT_SECRET` | JWT 토큰 서명 시크릿 키 (최소 32바이트) | dev: `ongo-dev-jwt-secret-key-must-be-at-least-32-bytes-long`, prod: 없음 | **필수 (prod)** |
| `PLATFORM_TOKEN_ENCRYPTION_KEY` | 플랫폼 토큰 AES-256 암호화 키 (Base64) | dev: `b25nby1kZXYtYWVzMjU2LWVuY3J5cHRpb24ta2V5MDA=`, prod: 없음 | **필수 (prod)** |
| `APP_BASE_URL` | 공개 OAuth callback과 웹 리디렉션의 운영 기준 URL | `http://localhost` (dev) | **필수 (prod)** |

---

## 3. OAuth 2.0 소셜 로그인

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 클라이언트 ID | `your-client-id` | **필수** |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 2.0 클라이언트 시크릿 | `your-client-secret` | **필수** |
| `KAKAO_CLIENT_ID` | Kakao OAuth 2.0 클라이언트 ID | `your-client-id` | **필수** |
| `KAKAO_CLIENT_SECRET` | Kakao OAuth 2.0 클라이언트 시크릿 | `your-client-secret` | **필수** |
| `OAUTH_STATE_SECRET` | Google Drive OAuth state 서명용 시크릿(최소 32자) | 로컬 전용 기본값 | **필수 (prod)** |

Google/Kakao 로그인 URL과 채널 연결 URL은 서버가 위 자격 증명으로 생성합니다. 프론트엔드
`VITE_*_CLIENT_ID`를 별도로 넣지 않아도 되며, 브라우저 번들에 OAuth client ID 설정을 복제하지
않습니다. Kakao 계정 이메일 동의를 거부하거나 앱 권한이 없으면 서버가 계정 생성을 진행하지
않고 로그인 오류로 종료합니다.

---

## 4. AI 서비스

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic Claude API 키 (메타데이터·해시태그·분석 생성 등) | `dummy-anthropic-key` (dev) | 선택 — prod에서 아래 AI 키 중 **1개 이상 필수** |
| `OPENAI_API_KEY` | OpenAI API 키 (Whisper STT 등) | `dummy-openai-key` (dev) | 선택 — prod에서 아래 AI 키 중 **1개 이상 필수** |
| `GEMINI_API_KEY` | Google Gemini API 키 | `dummy-gemini-key` (dev) | 선택 — prod에서 아래 AI 키 중 **1개 이상 필수** |
| `DASHSCOPE_API_KEY` | Alibaba DashScope API 키 (Qwen·Kimi·GLM·MiniMax) | `dummy-dashscope-key` (dev) | 선택 — prod에서 아래 AI 키 중 **1개 이상 필수** |

운영에서는 더미·placeholder 값이 공급자 자격 증명으로 인정되지 않습니다. 실제 키가 설정된
공급자만 capability와 AI 요청 대상이 되며, 하나도 설정되지 않으면 백엔드가 기동하지 않습니다.

---

## 5. 스토리지 (MinIO / Cloudflare R2)

### 로컬 개발 (MinIO)

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `MINIO_ACCESS_KEY` | MinIO 접속 액세스 키 | `minioadmin` | 선택 (로컬) |
| `MINIO_SECRET_KEY` | MinIO 접속 시크릿 키 | `minioadmin` | 선택 (로컬) |

### Docker Compose

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `MINIO_ROOT_USER` | MinIO 컨테이너 루트 사용자 | `minioadmin` | 선택 |
| `MINIO_ROOT_PASSWORD` | MinIO 컨테이너 루트 비밀번호 | `minioadmin` | 선택 |

### 프로덕션 (Cloudflare R2)

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `R2_ACCOUNT_ID` | Cloudflare 계정 ID (R2 엔드포인트 구성에 사용) | 없음 | **필수 (prod)** |
| `R2_ACCESS_KEY` | R2 API 토큰 액세스 키 | 없음 | **필수 (prod)** |
| `R2_SECRET_KEY` | R2 API 토큰 시크릿 키 | 없음 | **필수 (prod)** |
| `R2_BUCKET` | R2 버킷 이름 | `ongo-videos` | 선택 |

> R2는 S3 호환 API를 제공하며, 이그레스 비용이 $0입니다. R2 대시보드에서 API 토큰을 생성하여 위 환경변수를 설정하세요.

---

## 6. 결제 (PortOne V2)

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `PORTONE_STORE_ID` | PortOne 상점 ID | 없음 | **필수 (prod)** |
| `PORTONE_CHANNEL_KEY` | 결제 채널 키 | 없음 | **필수 (prod)** |
| `PORTONE_API_SECRET` | 서버 결제 조회용 API Secret | 없음 | **필수 (prod)** |
| `PORTONE_WEBHOOK_SECRET` | 웹훅 서명 검증용 시크릿 (`PORTONE_API_SECRET`과 별도) | 없음 | **필수 (prod)** |

---

## 7. 플랫폼 API

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `GOOGLE_API_KEY` | Google/YouTube Data API 키 | 빈 문자열 | **필수 (prod)** |
| `TIKTOK_CLIENT_KEY` / `TIKTOK_CLIENT_SECRET` | TikTok Login Kit·Content Posting API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `INSTAGRAM_APP_ID` / `INSTAGRAM_APP_SECRET` | Meta 앱의 Instagram Graph API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `FACEBOOK_APP_ID` / `FACEBOOK_APP_SECRET` | Meta Facebook Pages API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | Meta Threads API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `TWITTER_CLIENT_ID` / `TWITTER_CLIENT_SECRET` | X OAuth 2.0 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `PINTEREST_APP_ID` / `PINTEREST_APP_SECRET` | Pinterest API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `LINKEDIN_CLIENT_ID` / `LINKEDIN_CLIENT_SECRET` | LinkedIn API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `WORDPRESS_CLIENT_ID` / `WORDPRESS_CLIENT_SECRET` | WordPress.com OAuth 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `DAILYMOTION_API_KEY` / `DAILYMOTION_API_SECRET` | Dailymotion API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `VIMEO_CLIENT_ID` / `VIMEO_CLIENT_SECRET` | Vimeo API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |
| `TUMBLR_CONSUMER_KEY` / `TUMBLR_CONSUMER_SECRET` | Tumblr API 자격 증명 | 빈 문자열 | 선택 — 미설정 시 UI에서 숨김 |

Naver Clip은 공개 업로드 API가 확인되지 않아 현재 연동·게시 목록에서 제외한다. 플랫폼 자격 증명이
없거나 더미 값이면 서버 capability가 `configurationAvailable=false`를 반환하고, 프론트는 OAuth 버튼을
열지 않는다.

---

## 8. CORS

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | 허용할 Origin 목록 (쉼표 구분). prod에서 비워 두면 `APP_BASE_URL`을 동일 출처로 사용 | `APP_BASE_URL` (prod), localhost 기본값 (dev) | **필수 (prod) / 선택 (dev)** |

---

## 환경별 설정 요약

### 로컬 개발 (dev 프로파일)

로컬 개발은 기본값으로 기동할 수 있지만, 더미 자격 증명은 외부 API 호출에 사용되지 않습니다.
OAuth와 AI 기능을 테스트하려면 해당 서비스의 실제 키를 설정해야 합니다.

```bash
# 최소 설정 (.env 또는 환경변수)
export DB_PASSWORD=ongo_password          # dev 기본값 있음
export JWT_SECRET=ongo-dev-jwt-secret-key-must-be-at-least-32-bytes-long  # dev 기본값 있음

# 소셜 로그인 테스트 시
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export KAKAO_CLIENT_ID=your-kakao-client-id
export KAKAO_CLIENT_SECRET=your-kakao-client-secret

# AI 기능 테스트 시
export ANTHROPIC_API_KEY=sk-ant-xxx
export OPENAI_API_KEY=sk-xxx
# 또는
export GEMINI_API_KEY=AIza-xxx
# 또는 Qwen/Kimi/GLM/MiniMax
export DASHSCOPE_API_KEY=sk-xxx
```

### 프로덕션 (prod 프로파일)

모든 `필수 (prod)` 항목을 반드시 설정해야 합니다.

```bash
# 데이터베이스
export DB_USERNAME=ongo_user
export DB_PASSWORD=<강력한_비밀번호>

# 보안
export JWT_SECRET=<최소_32바이트_랜덤_시크릿>
export PLATFORM_TOKEN_ENCRYPTION_KEY=<Base64_AES256_키>

# OAuth 2.0
export GOOGLE_CLIENT_ID=<google-client-id>
export GOOGLE_CLIENT_SECRET=<google-client-secret>
export KAKAO_CLIENT_ID=<kakao-client-id>
export KAKAO_CLIENT_SECRET=<kakao-client-secret>

# AI
export ANTHROPIC_API_KEY=<anthropic-api-key>
export OPENAI_API_KEY=<openai-api-key>
# 아래 중 하나 이상을 실제 값으로 설정하면 됩니다.
export GEMINI_API_KEY=<gemini-api-key>
export DASHSCOPE_API_KEY=<dashscope-api-key>

# 스토리지 (Cloudflare R2)
export R2_ACCOUNT_ID=<cloudflare-account-id>
export R2_ACCESS_KEY=<r2-access-key>
export R2_SECRET_KEY=<r2-secret-key>
export R2_BUCKET=ongo-videos

# 결제 (PortOne V2)
export PORTONE_STORE_ID=<portone-store-id>
export PORTONE_CHANNEL_KEY=<portone-channel-key>
export PORTONE_API_SECRET=<portone-api-secret>
export PORTONE_WEBHOOK_SECRET=<portone-webhook-secret>

# 플랫폼 API
export GOOGLE_API_KEY=<google-api-key>

# CORS (필요 시 커스터마이징)
export CORS_ALLOWED_ORIGINS=https://ongo.kr,https://www.ongo.kr
```

---

## 주의사항

- **시크릿 값은 절대 Git에 커밋하지 마세요.** `.env` 파일 사용 시 `.gitignore`에 포함되어 있는지 확인하세요.
- `JWT_SECRET`은 최소 32바이트 이상이어야 합니다.
- `PLATFORM_TOKEN_ENCRYPTION_KEY`는 AES-256 호환 Base64 인코딩된 키여야 합니다.
- 프로덕션에서는 `your-api-key`, `your-client-id` 등의 플레이스홀더 기본값이 그대로 사용되지 않도록 반드시 실제 값을 설정하세요.
# Optional staged feature controls

`CAPABILITIES_DISABLED` is an optional comma-separated list of capability keys (for example,
`ugc/shorts/runs,admin`). Disabled keys remain in `GET /api/v1/capabilities` with
`enabled=false`, allowing the frontend to hide them without maintaining a second feature list.
