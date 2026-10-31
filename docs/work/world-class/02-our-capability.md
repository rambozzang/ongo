# 우리 핵심 흐름 실동작 점검 (조정 세션)

작성 2026-10-31. 근거는 코드(파일:줄). 경쟁사 비교는 01-competitors.md 를 기준으로 갱신한다.

## A. 롱폼 → 쇼츠 (대표 기능) — 격차가 가장 크다

단계: TRANSCRIBE → REFRAME → SEGMENT → SUBTITLE → HOOK → TEMPLATE → RENDER_SPEC → VALIDATE → SCHEDULE (`domain/ugc/shorts/PipelineStage.kt`). 렌더는 파이프라인 밖 비동기 작업.

| 영역 | 지금 | 문제 |
|---|---|---|
| 세로 변환 | 영상을 보지 않은 LLM 이 제목·전사문 앞 2,000자만 보고 크롭 박스 **하나**를 실행 전체에 적용(`ReframeStageExecutor.kt:30-43`, `Orchestrator:382-390`). 필터 `crop,scale=1080:1920`(`FfmpegVideoRenderer.kt:105-107`) | 원본 해상도를 모르는 추측 좌표, 9:16 이 아니면 화면이 **늘어남**, 범위 밖 검증 없음, 인물 추적 없음. 인물이 잘리는 결과가 흔할 것 |
| 클립 선택 | 전사문 텍스트만 보고 LLM 이 구간 선택(`SegmentStageExecutor.kt:33-44`). 점수·순위 없음, 개수 상한 없음, 길이는 프롬프트 권고뿐 | 경계가 문장 중간에서 끊길 수 있음(문장·무음 경계 보정 없음). 어떤 클립이 좋은지 알려 주지 못함 |
| 자막 | Whisper **세그먼트(문장) 단위** 타이밍, ASS 번인(`SubtitleStageExecutor.kt:87-101`, `ShortsRenderSpecBuilder.kt:152-185`) | 단어 단위 강조(요즘 쇼츠 표준) 없음, 5~9자 줄 규칙이 실제로는 적용 안 됨(`:65-69`), 한국어 줄바꿈 없음, 자막 수정 경로 없음 |
| 후킹 | 선택 문구를 0초~끝까지 상단 고정 표시 | 처음 몇 초만 강하게 보여 주는 연출 없음 |
| 템플릿 | 폰트·크기·색·위치만 반영 | `backgroundStyle`(BLACK_BARS 등)·safeArea·참조 이미지가 렌더에 **안 쓰임** |
| 편집 | 미리보기·크롭 겹쳐 보기만(`ShortsPipelineDetailView.vue`) | 구간 다듬기·자막 수정·크롭 수정이 화면에도 API 에도 없음 — 단계 재실행뿐 |
| 검증 | 규칙 4개 + LLM 총평(영상·오디오는 보지 않음) | 실제 화면 깨짐·잘림을 못 잡음 |
| 게시 연결 | 자동 모드: 렌더 → 예약 → YouTube/TikTok/Instagram 게시 | 태그 빈 값, 썸네일 없음(`ShortsPublishAdapter.kt:49-61`) |

## B. 게시·분석·AI 메타데이터 루프

**업로드 → 게시 → 상태 추적 → 수집까지는 실제로 동작한다.** 인사이트 → 다음 콘텐츠는 게시 시간 추천만 실측으로 이어진다.

- 게시: YouTube·TikTok·Instagram(Reels/Stories)·Threads·Facebook·LinkedIn·Pinterest 공식 API 실게시, 게시 후 상태 폴링(15초)·UNCONFIRMED 수동 재확인.
  X 는 막힘, Naver Clip 은 공개 API 없음(모든 메서드 예외). 플랫폼 자체 예약은 YouTube 만, 나머지는 자체 예약기(60초).
- 불일치: Instagram 한도 Capability 1GB vs `InstagramStreamWriter.kt:48` 500MB.
- **Instagram 분석 위험(추정)**: `plays` 지표를 필수로 요구(`InstagramClient.kt:264,280`) — Meta 가 폐기했다면 IG 수집 전체 실패.
- AI 메타: 플랫폼 규칙이 YT·TikTok·IG 3개뿐(`PromptTemplates.kt:14-16`, TikTok 150자로 잘못 적힘), 해시태그 일괄 30개,
  규칙은 생성 때가 아니라 게시 때 거절로만 강제. **과거 성과를 반영하지 않는다**(`GenerateMetaUseCase.kt:20-26`).
- 분석: YouTube 만 기간 지표·수익, 나머지는 조회·좋아요·댓글·공유 4개 누적→차분. 유입·인구통계 수집 경로 없음(정직하게 "수집 안 함" 표시).
- **신뢰 문제 — AI 가 지어낸 수치를 표시**: 키워드 검색량·경쟁도, 콘텐츠 갭 수요, 채널 감사 점수·성장 전망, 수익 예측 시나리오, SEO 점수 등(`PromptTemplates.kt`). "AI 추정" 표시 없음.
- **고장 난 버튼**: `VideosView.vue:941-957` 조회수 예측 → `/videos/{id}/predict-views` 백엔드 매핑 없음.
- 쓰이지 않는 것: 컴포넌트 204개 중 41개 미사용, application 모듈 87개 중 29개는 DTO 만.

## 2순위 목록 (1순위 쇼츠 이후)
1. AI 추정 수치에 "AI 추정·근거" 표시 또는 실측 대체 — 신뢰(조용한 실패·가짜 숫자 없음이 차별점)
2. Instagram `plays` → `views` 대응, 한도 불일치 수정, 고장 난 조회수 예측 버튼 연결(실측 회귀 있음)
3. **성과 기반 메타데이터** — 내 영상 중 성과 좋은 제목·태그 패턴을 프롬프트에 근거로 넣기(루프를 닫는 차별점), 플랫폼 규칙 전 플랫폼
4. AI 성과 요약 "이번 주 할 일"(한국어, 데이터 부족하면 결론 안 냄)
5. 네이버 클립 반자동(규격 파일 + 메타 준비 + PC 웹 업로드로 이동) — 완전 자동처럼 광고하지 않는다
