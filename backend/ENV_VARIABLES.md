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

---

## 3. OAuth 2.0 소셜 로그인

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 클라이언트 ID | `your-client-id` | **필수** |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 2.0 클라이언트 시크릿 | `your-client-secret` | **필수** |
| `KAKAO_CLIENT_ID` | Kakao OAuth 2.0 클라이언트 ID | `your-client-id` | **필수** |
| `KAKAO_CLIENT_SECRET` | Kakao OAuth 2.0 클라이언트 시크릿 | `your-client-secret` | **필수** |

---

## 4. AI 서비스

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic Claude API 키 (메타데이터 생성, 아이디어 추천 등) | `your-api-key` | **필수** |
| `OPENAI_API_KEY` | OpenAI API 키 (Whisper STT 등) | `your-api-key` | **필수** |

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

## 6. 결제 (Toss Payments)

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `TOSS_WEBHOOK_SECRET` | Toss Payments 웹훅 시크릿 | 빈 문자열 | **필수 (prod)** |

---

## 7. 플랫폼 API

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `GOOGLE_API_KEY` | Google/YouTube Data API 키 | 빈 문자열 | **필수 (prod)** |

---

## 8. CORS

| 환경변수 | 설명 | 기본값 | 필수 여부 |
|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | 허용할 Origin 목록 (쉼표 구분) | `http://localhost:3000,http://localhost:3001,http://localhost:5173,https://ongo.kr,https://www.ongo.kr` | 선택 |

---

## 환경별 설정 요약

### 로컬 개발 (dev 프로파일)

대부분 기본값이 제공되므로 별도 설정 없이 실행 가능합니다.
OAuth, AI API 키만 설정하면 해당 기능을 테스트할 수 있습니다.

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

# 스토리지 (Cloudflare R2)
export R2_ACCOUNT_ID=<cloudflare-account-id>
export R2_ACCESS_KEY=<r2-access-key>
export R2_SECRET_KEY=<r2-secret-key>
export R2_BUCKET=ongo-videos

# 결제
export TOSS_WEBHOOK_SECRET=<toss-webhook-secret>

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
