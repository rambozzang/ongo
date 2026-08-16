# 5건 재검토 — 분류와 최소 검증

> **역사적 스냅샷:** 아래 내용은 2026-08-11 당시 상태를 기록한 것이다. 이후 OAuth URL 생성은
> 서버 소유 방식으로 전환됐으므로, 현재 환경변수·연동 계약은 운영 체크리스트와 소스의
> `AuthOAuthAuthorizationAdapter`/`PlatformOAuthAuthorizationAdapter`를 기준으로 한다.

2026-08-11 · HEAD `b09a288d` · 코드/DB/시크릿/운영 서버 **미수정**
Codex `term_9b4e31d8` ↔ Claude `term_43c5c80d` 공동 검토 결과

| # | 항목 | 분류 | 승인 필요 |
|---|---|---|---|
| ③ | TikTok OAuth scope 불일치 | **지금 고칠 코드** | 불필요 |
| ⑤ | axios 로그인 401 refresh | **지금 고칠 코드** | 불필요 |
| ④ | 배포 assets 구버전 탭 | **운영 승인 필요** | 서버 수동 반영 |
| ② | TikTok client key 반영 | **운영 승인 필요** | 🔒 사용자 명시 승인 |
| ① | user id=6 삭제 | **보류** | 🔒 사용자 명시 승인 |

---

## ③ TikTok OAuth scope — 지금 고칠 코드

**근거**: 백엔드는 Direct Post(`/v2/post/publish/video/init/`, `TikTokApi.kt:18`)라 `video.publish` 가 필요하다.
`frontend/src/utils/oauth.ts` 는 `video.upload,video.list` 만 요청한다. 저장소 안에 정본이 이미 있다 —
`PublicOAuthUseCase.kt:183` = `"video.publish,video.upload,video.list"` (`:180` 에 Direct Post 근거 주석).

**왜 승인이 불필요한가**: `VITE_TIKTOK_CLIENT_KEY` 가 없어 버튼이 이미 막혀 있다.
사용자 가시 변화 0, 회귀 위험 0. ② 결정 시 추가 작업이 없도록 미리 정렬해 두는 것.

**최소 검증**
1. `oauth.ts` 의 TIKTOK scope 문자열이 `PublicOAuthUseCase.kt:183` 과 **완전 일치**
2. `frontend/src/utils/oauth.test.ts` 에 scope 단언 추가 — `:35-38` 의 Dailymotion 케이스가 그대로 본보기다
3. `npx vitest run src/utils/oauth.test.ts` 통과 (건수까지 확인)
4. `npm run build` 통과 후 산출물 `dist/assets/oauth-*.js` 에서 scope 문자열 확인

**미해결 부수 사항**: inbox 엔드포인트(`/v2/post/publish/inbox/`) 호출부가 코드에 **0건**이라 `video.upload` 는 실제 미사용이다.
미사용 scope 는 심사 반려 위험이 있으나, 콘솔에서 제거 가능한지 확인 전까지는 정본(`:183`)을 따른다.

## ⑤ axios 로그인 401 refresh — 지금 고칠 코드

**근거**: `client.ts:62` 가 401 을 경로 구분 없이 refresh 분기로 보낸다.
`auth.ts:13` 이 `apiClient` 를 쓰고 `client.ts:40-44` 가 **경로와 무관하게** 토큰을 붙이므로,
stale token 이 있으면 로그인 요청에도 `Authorization` 이 실린다.

**구현 방식이 중요하다 — 반드시 경로 기반**
"Authorization 헤더가 없으면 건너뛰기"는 **틀렸다**(Claude 이전 제안, 정정함). 위 이유로 헤더가 실리기 때문이다.
제외 대상: `/auth/login/**`, `/auth/*/state`, `/auth/refresh`, `/auth/dev-login`.
이들은 `SecurityConfig` publicPaths 라 애초에 bearer 를 요구하지 않는다 → 401 은 항상 진짜 인증 실패다.

**재귀 위험 없음**: refresh 는 `apiClient` 가 아니라 맨 `axios.post` 로 호출돼 인터셉터를 타지 않는다(`client.ts:83`).
`isRefreshing` / `_retry` 가드도 그대로 유지한다.

**최소 검증** (`src/api/client.test.ts` 신규 — 현재 없음)
1. localStorage 에 stale accessToken 이 있는 상태에서 `/auth/login/google` 이 401 →
   **refresh 를 호출하지 않고** 원래 에러를 그대로 reject
2. 보호된 엔드포인트가 401 → refresh 가 호출된다 (**기존 동작 보존**, 이게 회귀 방어의 핵심)
3. `npx vitest run` 전체 통과 (현재 47 files / 207 tests 기준선)

## ④ 배포 assets 구버전 탭 — 운영 승인 필요

**근거**: `deploy/deploy.sh:272-280` 이 `www` 를 통째로 `mv` 후 새 빌드만 `cp` 한다 → 이전 청크 소멸.
`nginx-ongo.conf:9` 는 `try_files $uri $uri/ /index.html` 뿐이라 삭제된 JS 에 **200 text/html** 을 돌려준다.
MIME 오류가 나고 `router.onError` 자동 새로고침이 트리거되지 않는다.

**왜 운영 승인인가**: `deploy/oracle/nginx-ongo.conf` 는 **템플릿이고 배포 파이프라인이 서버에 적용하지 않는다.**
저장소를 고쳐도 운영은 바뀌지 않는다. 서버 수동 반영이 필수다.

**권장: 안㉯ 먼저** — `location /assets/ { try_files $uri =404; }`
캐시 전략이 아니라 정합성 수정이다. 배포마다 재발하는 유일한 항목이라 실익이 가장 크다.

**최소 검증** (반영 전/후 대조)
1. **반영 전 재현**: 존재하지 않는 `/assets/index-XXXX.js` 요청 → 현재 `200 text/html` 확인
2. **반영 후**: 같은 요청 → `404`
3. **회귀 확인 (핵심 리스크)**: 실재 청크는 여전히 `200 application/javascript`,
   SPA 라우트(`/today` 등)는 여전히 `index.html` 반환
4. nginx 반영은 `nginx -t` 후 `reload` (재시작 아님)

**안㉮(`deploy.sh` 가 직전 백업 assets 를 `cp -n`)는 후순위** — 효과는 크지만 배포 스크립트 실패 시
배포 자체가 막히고, 검증에 실제 배포 1회를 태워야 해 비용이 크다.

## ② TikTok client key 반영 — 운영 승인 필요 🔒

**실행하지 않았다.** 사용자 명시 승인 전까지 저장소·서버 반영 금지.

**사유 정정**: `client_key` 는 **비밀값이 아니라 공개 식별자**다.
`oauth.ts:70` 이 브라우저 인가 URL 에 싣고, `VITE_` 접두 값은 정의상 번들에 컴파일된다.
`VITE_GOOGLE_CLIENT_ID` 도 이미 저장소에 커밋돼 있다. 비밀은 `client_secret` 이며 서버 `.env` 전용이다.

따라서 보류 사유는 보안이 아니라 **제품 결정**이다 — "게시가 불가능한 연동 버튼을 지금 열 것인가".
연다면: Sandbox 테스트와 심사용 데모 영상 촬영이 가능해진다(심사 필수 항목).
열지 않으면: 그만큼 심사 일정이 늦어진다.

**전제**: ③ 과 **함께** 반영해야 한다. ② 만 하면 연결은 되고 게시가 권한 오류로 실패한다.

**최소 검증** (승인 후)
1. 빌드 산출물 `dist/assets/oauth-*.js` 에 `client_key:"awni…"` 가 실제로 박혔는지
   (`void 0` / `""` 아님 — 구글 때 이 확인을 빠뜨려 하루를 썼다)
2. 연결 버튼 → TikTok 인가 화면 도달, `redirect_uri_mismatch` 없음
3. scope 파라미터에 `video.publish` 포함 확인

## ① user id=6 삭제 — 보류 🔒

**SQL 을 실행하지 않았다.** 사용자 명시 승인 전까지 실행 금지.

**보류 권고 (Claude 초기 의견에서 변경)**: 처음엔 "1분, 위험 없음, 1순위"로 올렸으나 철회한다.
(a) 오늘 아무것도 차단하지 않는다 — 신규 가입은 `provider_id` 로 조회되어 정상 생성된다(id=7 이 실증).
(b) 계정 삭제 기능이 **P0 로 미구현**인데, id=6 은 그 기능의 **유일한 실제 픽스처**다.
손으로 지우면 E2E 로 검증할 대상이 사라진다.

**권장 경로**: 계정 삭제 기능 완성 후 **그 기능으로** 삭제 → 정리와 기능 검증을 겸한다.

**최소 검증** (만약 수동 삭제를 승인한다면, 실행 *전*에)
1. `users` 를 참조하는 FK 중 `ON DELETE CASCADE` 가 **아닌** 것 전수 확인
   (`information_schema.referential_constraints` 조회. 가정 금지 — 참조 테이블이 많다)
2. id=6 연관 행 건수 사전 집계(`user_settings`, `ai_credits`, `refresh_tokens` 등)
3. 최근 DB 덤프 **실재** 확인 (`deploy/backup_local_db.sh` 는 스크립트일 뿐 실행 이력이 아니다)
4. 트랜잭션 안에서 삭제 후 잔여 0건 확인

---

## 권장 순서

```
③ + ⑤ (코드, 승인 불필요, 사용자 가시 변화 없음)
  → ④㉯ (서버 수동 반영 — 배포마다 재발하는 유일한 항목)
  → ② (사용자 결정 후, ③ 과 함께)
  → ① (계정삭제 기능 완성 후)
```

## 이번 검토에서 뒤집힌 판단 3건

1. **⑤ 구현 방식** — "헤더 없으면 건너뛰기"(Claude) → 로그인 요청도 stale 헤더를 실으므로 **경로 기반**이어야 한다.
   Codex 의 소스 대조로 확인됐다.
2. **② 보류 사유** — "client_key 는 비밀값"(Codex) → 공개 식별자다. 결론은 같지만 이유가 다르다.
   고쳐두지 않으면 앞으로 모든 `VITE_` 값을 비밀 취급하게 된다.
3. **① 우선순위** — "1순위, 즉시 삭제"(Claude) → **보류**. 계정삭제 기능의 픽스처를 없애면 안 된다.
