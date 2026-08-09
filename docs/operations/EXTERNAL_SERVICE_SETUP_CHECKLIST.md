# ongo 외부 서비스 앱 등록·키 발급 체크리스트

> 소스 확인일: 2026-07-18  
> 운영 웹 도메인 가정: `https://ongo.codelabtiger.com`  
> 목적: ongo를 실제 서비스로 운영하기 위해 외부 서비스에 가입하고 앱·API 키·OAuth·웹훅을 등록하는 순서

## 1. 먼저 알아둘 점

- 비밀키는 채팅, 메신저, Git, 문서에 붙여 넣지 않는다.
- 브라우저에 들어가는 `VITE_*_CLIENT_ID`, App ID, Client Key는 공개 식별자다. Client Secret은 절대 `VITE_*`에 넣지 않는다.
- 발급한 비밀값은 서버의 secret manager 또는 Git에서 제외된 `.env.production`에 저장한다.
- 개발용 앱과 운영용 앱은 가능하면 분리한다.
- OAuth 검수를 신청하기 전에 서비스 홈페이지, 개인정보처리방침, 이용약관, 계정·데이터 삭제 안내 URL이 실제 접속 가능해야 한다.
- 현재 프런트 OAuth callback은 모두 웹 프런트로 돌아온 뒤 백엔드에서 code를 교환하는 구조다.

## 2. 등록 전에 확정할 URL

현재 소스가 사용하는 운영 URL은 다음과 같다.

| 용도 | 등록할 운영 URL |
|---|---|
| 서비스 홈페이지 | `https://ongo.codelabtiger.com` |
| Google 로그인 callback | `https://ongo.codelabtiger.com/auth/callback/google` |
| Kakao 로그인 callback | `https://ongo.codelabtiger.com/auth/callback/kakao` |
| SNS 채널 연결 공통 callback | `https://ongo.codelabtiger.com/auth/channel-callback` |
| Google Drive callback | `https://ongo.codelabtiger.com/api/v1/content-sources/google-drive/callback` |
| **PortOne webhook (현재 결제)** | `https://ongo.codelabtiger.com/api/v1/portone/webhook` |
| Paddle webhook (레거시) | `https://ongo.codelabtiger.com/api/v1/paddle/webhooks` |
| Toss webhook (레거시) | `https://ongo.codelabtiger.com/api/v1/payments/webhook` |
| 개인정보처리방침 | `https://ongo.codelabtiger.com/privacy` 또는 실제 공개 URL |
| 이용약관 | `https://ongo.codelabtiger.com/terms` 또는 실제 공개 URL |
| 계정/데이터 삭제 안내 | 실제 공개 URL을 새로 준비해야 함 |

로컬 callback은 프런트의 실제 포트에 맞춰 다음을 사용한다.

```text
http://localhost:5173/auth/callback/google
http://localhost:5173/auth/callback/kakao
http://localhost:5173/auth/channel-callback
```

주의: `application.yml`의 서버 포트는 `8070`, `frontend/.env.development`는 API를 `8777`로 가리키며 Google Drive 기본 URL도 `8777`이다. 프록시가 8777을 사용하지 않는다면 앱 등록 전 로컬 포트를 하나로 정리해야 한다. TikTok Web은 운영 callback에 HTTPS를 요구하므로 localhost 대신 공개 HTTPS 개발 도메인 또는 tunnel을 사용한다.

## 3. 오늘 바로 등록할 서비스

### 3.1 Google Cloud — 로그인, YouTube, Google Drive

공식 시작 문서: [YouTube OAuth 웹 서버 앱](https://developers.google.com/youtube/v3/guides/auth/server-side-web-apps)

한 Google Cloud 프로젝트 `ongo-production`에서 시작해도 된다.

진행 순서:

1. Google Cloud Console에서 프로젝트 생성
2. OAuth consent screen에서 앱 이름 `ongo`, 지원 이메일, 홈페이지, 개인정보처리방침, 약관 등록
3. 테스트 단계에서는 본인과 테스터 Google 계정 등록
4. API Library에서 다음 API 활성화
   - YouTube Data API v3
   - Google Drive API
5. OAuth Client를 `Web application` 유형으로 생성
6. Authorized JavaScript origins 등록
   - `http://localhost:5173`
   - `https://ongo.codelabtiger.com`
7. Authorized redirect URIs 등록
   - Google 로그인 callback
   - SNS 채널 연결 공통 callback
   - Google Drive callback
8. 가능하면 로그인/YouTube용과 Drive용 OAuth Client를 분리한다. 현재 소스는 Drive 전용 키도 지원한다.

받아야 할 값:

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_API_KEY=
GOOGLE_DRIVE_CLIENT_ID=
GOOGLE_DRIVE_CLIENT_SECRET=

# frontend build-time public value
VITE_GOOGLE_CLIENT_ID=
```

현재 소스 scope 확인:

- 로그인: `openid email profile`
- YouTube: 현재 프런트는 광범위한 `https://www.googleapis.com/auth/youtube`를 요청
- Google Drive: `drive.readonly`

운영 공개 전 Google OAuth verification이 필요할 수 있다. YouTube는 서비스 계정 방식이 일반 채널에 지원되지 않으므로 사용자 OAuth가 반드시 필요하다.

완료 체크:

- [ ] Google 로그인 성공
- [ ] refresh token 저장 확인
- [ ] YouTube 테스트 채널 연결
- [ ] 비공개 영상 1개 업로드
- [ ] YouTube 지표 동기화
- [ ] Google Drive 파일 목록 및 가져오기

### 3.2 Kakao Developers — 사용자 로그인

공식 설정 문서: [Kakao Login 사전 설정](https://developers.kakao.com/docs/en/kakaologin/prerequisite)

진행 순서:

1. Kakao Developers에서 애플리케이션 `ongo` 생성
2. Web 플랫폼에 운영 사이트 도메인 등록
3. Kakao Login 활성화
4. REST API Key의 Redirect URI에 Kakao 로그인 callback 등록
5. 동의항목에서 닉네임과 이메일 설정
6. Client secret을 생성하고 활성화
7. 개발 단계에서 팀원 계정을 앱 멤버로 초대

받아야 할 값:

```dotenv
KAKAO_CLIENT_ID=        # REST API Key
KAKAO_CLIENT_SECRET=    # 활성화한 Client secret

# frontend build-time public value
VITE_KAKAO_CLIENT_ID=   # REST API Key와 동일
```

완료 체크:

- [ ] Kakao 로그인 활성화
- [ ] redirect URI 정확히 일치
- [ ] 이메일 동의 권한 상태 확인
- [ ] 테스트 계정 로그인

### 3.3 TikTok for Developers — 채널 연결과 게시

공식 문서: [TikTok 앱 생성](https://developers.tiktok.com/doc/getting-started-create-an-app), [Content Posting API](https://developers.tiktok.com/doc/content-posting-api-get-started)

진행 순서:

1. TikTok for Developers 계정 생성 및 조직 정보 등록
2. Web 앱 `ongo` 생성
3. Login Kit 추가
4. Content Posting API 추가, Direct Post 설정 활성화
5. 운영 redirect URI로 SNS 채널 연결 공통 callback 등록
6. 홈페이지, 개인정보처리방침, 이용약관 URL 등록 및 도메인 소유권 확인
7. 필요한 scope를 신청
   - `user.info.basic`
   - `video.list`
   - `video.upload`
   - 직접 공개 게시에는 `video.publish`
8. Sandbox에서 테스트 후 앱 심사 및 Direct Post audit 신청

받아야 할 값:

```dotenv
TIKTOK_CLIENT_KEY=
TIKTOK_CLIENT_SECRET=
VITE_TIKTOK_CLIENT_KEY=
```

중요: 현재 프런트는 `video.upload,video.list`만 요청하지만 백엔드는 Direct Post 형태다. 공개 직접 게시에는 `video.publish` 승인과 프런트 scope 수정이 필요하다. audit 전 직접 게시 콘텐츠는 비공개로 제한된다.

완료 체크:

- [ ] Login Kit 등록
- [ ] Content Posting API 추가
- [ ] URL ownership 확인
- [ ] sandbox 사용자 연결
- [ ] 비공개 테스트 업로드
- [ ] `video.publish` 및 audit 신청

### 3.4 Meta Developers — Instagram, Facebook, Threads

공식 포털: [Meta for Developers](https://developers.facebook.com/)

우선 하나의 Business 앱을 생성하되, Meta 대시보드에서 제품별 요구사항에 따라 별도 앱이 필요하다고 안내되면 Threads를 분리한다.

준비 사항:

- Meta Business Portfolio
- 테스트용 Facebook Page
- Page에 연결된 Instagram Professional 계정
- 테스트용 Threads 계정
- 사업자 정보, 개인정보처리방침, 데이터 삭제 안내

신청할 기능과 권한:

- Facebook Pages
  - `pages_manage_posts`
  - `pages_read_engagement`
  - `pages_show_list`
- Instagram 게시
  - 현재 코드가 요청하는 `instagram_basic`, `instagram_content_publish`
  - 사용하는 로그인 방식과 API 버전에 맞춰 App Review에서 실제 권한명을 다시 확인
- Threads
  - `threads_basic`
  - `threads_content_publish`
  - `threads_manage_insights`

등록할 callback:

```text
https://ongo.codelabtiger.com/auth/channel-callback
```

받아야 할 값:

```dotenv
FACEBOOK_APP_ID=
FACEBOOK_APP_SECRET=
THREADS_APP_ID=
THREADS_APP_SECRET=

VITE_FACEBOOK_APP_ID=
VITE_INSTAGRAM_CLIENT_ID=  # 실제 Instagram OAuth client/app ID
VITE_THREADS_APP_ID=
```

현재 소스에서 Instagram 백엔드 Client는 `platform.facebook.app-id/app-secret`을 공유한다. 그런데 프런트에는 별도 `VITE_INSTAGRAM_CLIENT_ID`가 있다. 발급 후 실제 Meta 앱 구성에 맞춰 동일 App ID를 넣을지 Instagram 전용 ID를 넣을지 통합 테스트로 확정해야 한다.

완료 체크:

- [ ] Business 앱과 비즈니스 인증 준비
- [ ] Facebook 테스트 Page 연결 및 게시
- [ ] Instagram Professional 계정 연결 및 Reels 게시
- [ ] Threads 연결 및 게시
- [ ] 각 제품 App Review 제출
- [ ] 데이터 삭제 callback/안내 등록

### 3.5 Naver Developers — 현재 Naver Clip 연결 후보

공식 포털: [NAVER Developers](https://developers.naver.com/main/)

진행 순서:

1. 애플리케이션 등록
2. 사용 API로 네이버 로그인 선택
3. 서비스 URL과 SNS 채널 연결 공통 callback 등록
4. 제공 정보와 검수 요구사항 확인

받아야 할 값:

```dotenv
VITE_NAVER_CLIENT_ID=
```

중요: 현재 `NaverClipClient`는 Naver OAuth endpoint를 사용하지만 `application.yml`에 Naver client ID/secret 서버 설정이 없다. 프런트 ID만 발급받아도 token exchange에 필요한 secret 매핑이 완성되지 않는다. 또한 Naver Clip의 일반 공개 업로드 API 사용 가능 여부와 제휴 조건을 Naver에 직접 확인해야 한다. 따라서 지금 앱은 등록하되 실제 Clip 게시 가능 판정을 받기 전 판매 기능으로 약속하지 않는다.

문의 시 확인할 항목:

- 제3자 SaaS에서 사용자 Clip 업로드가 가능한지
- 공개 API 또는 제휴 API 신청 절차
- 게시, 목록, 지표 조회 권한
- 검수와 사업자 요건

### 3.6 X Developer Platform — 게시와 조회

공식 포털: [X Developer Platform](https://developer.x.com/)

진행 순서:

1. Developer account와 Project/App 생성
2. OAuth 2.0 활성화, 앱 유형 Web App 설정
3. callback에 SNS 채널 연결 공통 callback 등록
4. Website URL 등록
5. Read and write 권한과 필요한 유료 API plan 확인

현재 코드 scope:

```text
tweet.read tweet.write users.read offline.access
```

받아야 할 값:

```dotenv
TWITTER_CLIENT_ID=
TWITTER_CLIENT_SECRET=
VITE_TWITTER_CLIENT_ID=
```

완료 체크:

- [ ] OAuth 2.0 PKCE 연결
- [ ] refresh token 발급
- [ ] 텍스트/미디어 게시
- [ ] 현재 요금제의 읽기·쓰기 제한 기록

## 4. 제품 운영 전에 등록할 서비스

### 4.1 Cloudflare R2 — 운영 영상 저장소

공식 문서: [R2 S3 API 자격증명](https://developers.cloudflare.com/r2/get-started/s3/)

1. R2 구매/활성화
2. 운영 bucket `ongo-videos` 생성
3. 해당 bucket에만 `Object Read & Write` 권한을 가진 API token 생성
4. Access Key ID와 Secret Access Key를 즉시 안전하게 저장
5. 필요하면 bucket CORS와 custom domain 설정

받아야 할 값:

```dotenv
R2_BUCKET=ongo-videos
R2_ACCOUNT_ID=
R2_ACCESS_KEY=
R2_SECRET_KEY=
```

Secret Access Key는 생성 시 다시 볼 수 없으므로 반드시 secret manager에 저장한다.

### 4.2 PortOne V2 — SaaS 구독과 AI 크레딧 결제 (**현재 사용 중, 릴리스 블로커**)

공식 포털: [포트원 개발자센터](https://developers.portone.io/opi/ko/integration/start/v2/readme?v=v2)

체크아웃은 전부 PortOne V2로 동작한다. 아래 4개를 모두 채우지 않으면 결제가 성립하지 않는다.

1. 포트원 관리자 콘솔에서 상점 생성 → **Store ID** 확보
2. 결제대행사(PG) 연동 후 채널 추가 → **채널 키** 확보
3. 결제 연동 → API Keys 에서 **API 시크릿** 발급
4. 결제 연동 → 연동 관리 → **결제알림(Webhook) 관리**
   - 웹훅 URL 등록: `https://ongo.codelabtiger.com/api/v1/portone/webhook`
   - **"웹훅 시크릿 발급"** 버튼으로 시크릿 발급 (`whsec_` 접두사가 붙어 있어도 그대로 넣으면 된다)
   - 이 시크릿은 **API 시크릿과 다른 값**이다. 혼동하면 모든 웹훅이 거부된다
5. 샌드박스 E2E(결제 → 취소 → 부분취소) 확인 후 라이브 전환

받아야 할 값:

```dotenv
PORTONE_STORE_ID=
PORTONE_CHANNEL_KEY=
PORTONE_API_SECRET=
PORTONE_WEBHOOK_SECRET=
```

**`PORTONE_WEBHOOK_SECRET`이 비어 있으면 어떻게 되는가**

서버는 정상 기동하고 프론트에서 시작한 결제도 성립한다(프론트가 `/portone/payments/{id}/complete`를
직접 호출하기 때문). 그러나 **웹훅은 전량 400으로 거부**되고 `PortOneWebhookVerifier`가
`"포트원 웹훅 시크릿이 설정되지 않아 서명을 검증할 수 없습니다"` error 로그만 남긴다.
그 결과 다음이 반영되지 않는다.

- 결제창을 닫았지만 실제로는 승인된 건의 뒤늦은 정산
- **결제 취소·환불** — `payments`가 `COMPLETED`로 남고 크레딧 회수·구독 해제가 일어나지 않는다

즉 조용히 절반만 동작하는 상태가 된다. 배포 전 반드시 채운다.

> 이 값은 `ProductionConfigurationValidator`의 필수 항목에 **의도적으로 넣지 않았다.**
> 넣으면 값이 없을 때 서버가 기동조차 못 하는데, 과거 `PLATFORM_TOKEN_ENCRYPTION_KEY`와
> `GOOGLE_CLIENT_ID`로 배포가 막힌 이력이 있다. 대신 이 문서와 `deploy/start.sh`에서 관리한다.

### 4.3 Paddle — 레거시, 신규 결제에는 사용하지 않음

공식 포털: [Paddle Developer](https://developer.paddle.com/)

현재 체크아웃 UI는 Paddle을 호출하지 않는다(`usePaddle.ts`·`api/paddle.ts`는 참조하는 곳이 없다).
백엔드 Paddle 코드는 **기존 결제 레코드와 웹훅 처리용으로만** 남아 있다.
신규 구축이라면 이 절을 건너뛰어도 된다. 아래는 기존 Paddle 결제 이력이 있을 때만 필요하다.

1. Sandbox 계정에서 Business 정보와 Default payment link 설정
2. 월간/연간 Starter, Pro, Business product/price 생성
3. AI credit 4종 일회성 product/price 생성
4. Client-side token 생성
5. Backend API key 생성
6. Paddle webhook URL 등록 및 secret 저장
7. Sandbox E2E 완료 후 Live 전환과 판매자 심사

받아야 할 값:

```dotenv
PADDLE_API_KEY=
PADDLE_WEBHOOK_SECRET=
PADDLE_CLIENT_TOKEN=
PADDLE_ENVIRONMENT=sandbox
PADDLE_PRICE_STARTER=
PADDLE_PRICE_PRO=
PADDLE_PRICE_BUSINESS=
PADDLE_PRICE_STARTER_YEARLY=
PADDLE_PRICE_PRO_YEARLY=
PADDLE_PRICE_BUSINESS_YEARLY=
PADDLE_PRICE_CREDIT_STARTER=
PADDLE_PRICE_CREDIT_BASIC=
PADDLE_PRICE_CREDIT_PRO=
PADDLE_PRICE_CREDIT_BUSINESS=
```

현재 코드의 환경값 기본은 `live`이고 배포 예제는 `production`이다. Paddle 설정 클래스가 실제로 허용하는 값과 SDK 분기를 확인해 `sandbox`/`live` 중 하나로 통일한 뒤 키를 넣는다.

### 4.4 Toss Payments — 레거시, 후순위

공식 포털: [Toss Payments 개발자센터](https://developers.tosspayments.com/)

현재 소스는 Toss 결제 승인 client key/secret key가 아니라 webhook secret만 설정한다. 즉, 완전한 Toss 결제 플로우가 아니다. PortOne V2를 주 결제로 사용하므로 Toss 계약은 미뤄도 된다.

```dotenv
TOSS_WEBHOOK_SECRET=
```

Toss를 실제 주 결제로 사용하려면 결제창 client key, server secret key, 승인 API와 실패/취소 흐름을 별도 구현해야 한다.

### 4.5 호스트 바이너리 — yt-dlp (URL 임포트에 필요)

외부 서비스 가입이 아니라 **서버에 직접 설치**해야 하는 의존성이다.

YouTube/TikTok/Instagram URL 임포트 기능이 `yt-dlp` 와 `ffmpeg` 를 호출한다.
`deploy/start.sh` 가 JAR 을 호스트에서 직접 실행하므로(`setsid java -jar`)
도커 이미지에 넣는 방식이 통하지 않는다. Oracle 호스트에 설치해야 한다.

```bash
# Ubuntu/Debian 기준
sudo apt-get update && sudo apt-get install -y ffmpeg
sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
sudo chmod a+rx /usr/local/bin/yt-dlp
yt-dlp --version && ffmpeg -version | head -1
```

설치하지 않으면 임포트 요청이 실패한다. 서버 기동 자체는 막지 않는다.
다른 경로에 설치했다면 환경변수로 재정의한다.

```dotenv
# 선택. 기본값은 PATH 의 yt-dlp
YT_DLP_PATH=

# 쇼츠 서버 렌더용 ffmpeg. 운영 호스트는 PATH 에 없으므로 절대 경로가 필수다 (4.5.1 참조)
FFMPEG_PATH=/data/ffmpeg/bin/ffmpeg
```

> `yt-dlp` 는 대상 사이트 변경에 맞춰 자주 갱신된다. 임포트가 갑자기 실패하면
> 먼저 바이너리를 최신으로 올려본다.

### 4.5.1 호스트 바이너리 — ffmpeg (쇼츠 서버 렌더에 필요)

쇼츠 클립을 **서버에서** 인코딩한다. 예전에는 `render.sh` 를 zip 으로 내려주고 사용자가
자기 PC 에서 돌렸지만 이제 서버가 직접 만든다.

**운영 호스트 설치 위치 (2026-08-08 확인)**

```
/data/ffmpeg/bin/ffmpeg
/data/ffmpeg/bin/ffprobe
```

**PATH 에 없다.** 절대 경로를 환경변수로 주입해야 한다.

```bash
FFMPEG_PATH=/data/ffmpeg/bin/ffmpeg
```

빠뜨리면 렌더가 전부 실패한다. 다만 **서비스가 죽지는 않는다** — 가용성 조회가
`available=false` 를 돌려주고 화면이 렌더 버튼을 감춘다. 그래서 필수 환경변수 목록
(`deploy/required-env.sh`)에는 넣지 않았다. 넣으면 값이 없을 때 배포 자체가 막힌다.

**설정이 맞는지 확인하는 법**

```
GET /api/v1/ugc/shorts/render/availability
```

`{ "available": true }` 면 정상이고, `false` 면 경로가 틀렸거나 실행 권한이 없다.
배포 직후 한 번 호출해 보면 된다. **서버에 직접 들어가지 않고 확인할 수 있는 수단이다.**

**선택 설정**

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `SHORTS_RENDER_MAX_CONCURRENT` | 1 | 동시 인코딩 수. 올리면 CPU 경합으로 일반 API 가 느려진다 |
| `SHORTS_RENDER_TIMEOUT_SECONDS` | 1800 | 인코딩 상한 |
| `SHORTS_RENDER_CRF` | 20 | 낮을수록 고화질·큰 용량 |
| `SHORTS_RENDER_PRESET` | medium | 느릴수록 압축률이 좋다 |

`ffprobe` 는 지금 쓰지 않는다. 설치돼 있으므로 나중에 산출물 길이·해상도 검증에 쓸 수 있다.


### 4.6 Alibaba Cloud Model Studio — 현재 기본 AI 제공자

공식 포털: [Alibaba Cloud Model Studio](https://www.alibabacloud.com/help/en/model-studio/)

1. Alibaba Cloud 계정과 결제 수단 등록
2. 국제 리전에 맞는 Model Studio API key 생성
3. 사용 모델의 접근 가능 여부 및 quota 확인
4. 비용 알림과 한도 설정

```dotenv
DASHSCOPE_API_KEY=
```

현재 소스의 기본 endpoint는 국제 coding endpoint이므로 발급 리전과 호환되는 키인지 확인한다.

### 4.7 선택 AI 제공자

AI provider fallback 또는 비교 기능을 사용할 때만 발급한다.

```dotenv
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
GEMINI_ENABLED=false
GEMINI_PROJECT_ID=
GEMINI_LOCATION=us-central1
```

초기 비용을 줄이려면 DashScope 하나만 실제 키로 설정하고 나머지는 기능 플래그로 비활성화하는 편이 낫다.

## 5. 후순위 SNS 등록

아래 표에서 Pinterest·LinkedIn·WordPress.com·Dailymotion은 OAuth·업로드 계약과 연결 UI까지 구현되어 있으며 공개 식별자와 실계정 검증이 남아 있다. Tumblr와 Vimeo는 백엔드 client 코드는 있으나 최신 업로드 계약 및 연결 UI 검증이 끝나지 않아 아직 노출하지 않는다.

| 서비스 | 서버 환경변수 | 상태 |
|---|---|---|
| Pinterest | `PINTEREST_APP_ID`, `PINTEREST_APP_SECRET` | OAuth·보드 연결·동영상 Pin 서버 계약 구현. `VITE_PINTEREST_APP_ID` 설정 후 실계정 검증 필요 |
| LinkedIn | `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET` | OAuth·최신 Videos API 파트 업로드·UGC 게시 구현. `VITE_LINKEDIN_CLIENT_ID` 설정 후 실계정 검증 필요 |
| WordPress.com | `WORDPRESS_CLIENT_ID`, `WORDPRESS_CLIENT_SECRET` | OAuth·form 미디어 URL 업로드·video post 구현. `VITE_WORDPRESS_CLIENT_ID` 설정 후 실계정 검증 필요 |
| Tumblr | `TUMBLR_CONSUMER_KEY`, `TUMBLR_CONSUMER_SECRET` | 프런트 OAuth 미구현 |
| Vimeo | `VIMEO_CLIENT_ID`, `VIMEO_CLIENT_SECRET` | 프런트 OAuth 미구현 |
| Dailymotion | `DAILYMOTION_API_KEY`, `DAILYMOTION_API_SECRET` | OAuth·API v2 세션 multipart 업로드·프로필 영상 생성 구현. `VITE_DAILYMOTION_API_KEY` 설정 후 실계정 검증 필요 |

Tumblr와 Vimeo는 키를 지금 받아도 사용자가 연결할 수 없으므로 가입 시간을 쓰지 않는 것을 권장한다. Dailymotion은 API v2 Private API key와 callback URL을 발급해야 한다.

## 6. 직접 생성해야 하는 내부 비밀값

외부 가입 없이 안전한 난수로 만든다.

```bash
# JWT HMAC secret: Base64 64 bytes
openssl rand -base64 64

# 플랫폼 OAuth token AES-256 key: Base64 32 bytes
openssl rand -base64 32

# Google Drive OAuth state HMAC secret
openssl rand -base64 32
```

환경변수:

```dotenv
JWT_SECRET=
PLATFORM_TOKEN_ENCRYPTION_KEY=
OAUTH_STATE_SECRET=
```

운영 중 `PLATFORM_TOKEN_ENCRYPTION_KEY`를 잃거나 임의로 바꾸면 저장된 SNS token을 복호화할 수 없다. 반드시 백업 및 키 회전 절차를 별도로 둔다.

## 7. 최종 환경변수 템플릿

비밀값을 넣지 않은 복사용 목록이다.

```dotenv
# Runtime
DB_USERNAME=ongo
DB_PASSWORD=
JWT_SECRET=
PLATFORM_TOKEN_ENCRYPTION_KEY=
OAUTH_STATE_SECRET=
APP_BASE_URL=https://ongo.codelabtiger.com
CORS_ALLOWED_ORIGINS=https://ongo.codelabtiger.com

# Login + Google services
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_API_KEY=
GOOGLE_DRIVE_CLIENT_ID=
GOOGLE_DRIVE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=

# Core social publishing
TIKTOK_CLIENT_KEY=
TIKTOK_CLIENT_SECRET=
FACEBOOK_APP_ID=
FACEBOOK_APP_SECRET=
THREADS_APP_ID=
THREADS_APP_SECRET=
TWITTER_CLIENT_ID=
TWITTER_CLIENT_SECRET=

# Storage
R2_BUCKET=ongo-videos
R2_ACCOUNT_ID=
R2_ACCESS_KEY=
R2_SECRET_KEY=

# AI
DASHSCOPE_API_KEY=
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
GEMINI_ENABLED=false
GEMINI_PROJECT_ID=
GEMINI_LOCATION=us-central1

# PortOne V2 — 현재 결제. 4개 모두 필수
PORTONE_STORE_ID=
PORTONE_CHANNEL_KEY=
PORTONE_API_SECRET=
# 아래는 API 시크릿과 다른 값이다. 비우면 취소/환불 웹훅이 전량 거부된다
PORTONE_WEBHOOK_SECRET=

# Paddle — 레거시. 기존 결제 이력이 없으면 비워도 된다
PADDLE_API_KEY=
PADDLE_WEBHOOK_SECRET=
PADDLE_CLIENT_TOKEN=
PADDLE_ENVIRONMENT=sandbox
PADDLE_PRICE_STARTER=
PADDLE_PRICE_PRO=
PADDLE_PRICE_BUSINESS=
PADDLE_PRICE_STARTER_YEARLY=
PADDLE_PRICE_PRO_YEARLY=
PADDLE_PRICE_BUSINESS_YEARLY=
PADDLE_PRICE_CREDIT_STARTER=
PADDLE_PRICE_CREDIT_BASIC=
PADDLE_PRICE_CREDIT_PRO=
PADDLE_PRICE_CREDIT_BUSINESS=

# Optional Toss
TOSS_WEBHOOK_SECRET=
```

프런트 빌드 환경:

```dotenv
VITE_API_BASE_URL=/api/v1
VITE_GOOGLE_CLIENT_ID=
VITE_KAKAO_CLIENT_ID=
VITE_TIKTOK_CLIENT_KEY=
VITE_INSTAGRAM_CLIENT_ID=
VITE_NAVER_CLIENT_ID=
VITE_TWITTER_CLIENT_ID=
VITE_FACEBOOK_APP_ID=
VITE_THREADS_APP_ID=
VITE_PINTEREST_APP_ID=
VITE_LINKEDIN_CLIENT_ID=
VITE_WORDPRESS_CLIENT_ID=
VITE_DAILYMOTION_API_KEY=
```

## 8. 권장 가입 순서

오늘은 다음 순서로 진행한다.

1. Google Cloud 프로젝트와 OAuth Client
2. Kakao 앱
3. TikTok 앱과 Sandbox
4. Meta Business 앱과 테스트 Page/Instagram 계정
5. Cloudflare R2
6. DashScope
7. Paddle Sandbox
8. X Developer
9. Naver 앱 등록 및 Clip API 사용 가능 여부 문의

각 단계에서 기록할 것은 `서비스`, `앱/프로젝트 ID`, `발급일`, `소유 계정`, `운영/개발 구분`, `승인 상태`, `secret 저장 위치`, `등록 callback`, `신청 scope`다. 실제 secret 값은 기록표에 적지 않고 secret manager의 항목명만 적는다.

## 9. 소스에서 발견한 설정 보완 과제

키를 발급받은 뒤 Claude Code에 다음 수정 작업을 별도로 요청한다.

1. `backend/.env.example`과 `deploy/oracle/.env.production.example`에 누락된 SNS·Drive·Paddle price 변수를 추가
2. `frontend/.env.example`을 새로 만들고 모든 공개 OAuth ID를 문서화
3. 개발 API port `8070`/`8777` 불일치 해결
4. Instagram App ID의 백엔드/프런트 매핑 통일
5. Naver server-side client ID/secret 설정 추가 또는 지원 기능 숨김
6. TikTok scope를 Direct Post 구현과 일치시키고 audit 전 비공개 제한 안내
7. Paddle environment 허용값을 코드·배포 설정에서 통일
8. OAuth state를 단순 `PLATFORM|returnPath` 문자열이 아닌 서버 검증 가능한 난수로 변경
9. 운영 시작 시 dummy 기본값을 허용하지 않고 필수 키 누락이면 startup validation으로 실패 처리

키 발급만으로 연동이 완성되는 것은 아니다. 위 보완 과제와 각 플랫폼의 테스트·심사를 통과해야 판매 가능한 상태가 된다.
