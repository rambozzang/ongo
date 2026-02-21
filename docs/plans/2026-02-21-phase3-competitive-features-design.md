# Phase 3: 경쟁력 강화 5대 기능 설계

**Goal:** 세계적 크리에이터 플랫폼 대비 약점 영역을 보강하여 경쟁력 극대화

**기능 목록:**
1. AI 영상 리사이징 (MVP)
2. 트렌드 모니터링 (다중 소스)
3. 팬/오디언스 CRM (프로필 + 세그먼트)
4. 다국어 콘텐츠 자동화 (메타데이터 + 자막)
5. 브랜드 딜/스폰서십 (딜 트래커 + 미디어키트)

---

## 1. AI 영상 리사이징 (MVP)

**개념:** 업로드된 영상을 플랫폼별 최적 비율로 자동 변환

| 타겟 비율 | 용도 | FFmpeg 전략 |
|-----------|------|-------------|
| 9:16 | TikTok, Reels, Shorts | 중앙 기준 크롭 → 1080×1920 |
| 1:1 | Instagram 피드 | 중앙 기준 크롭 → 1080×1080 |
| 4:5 | Instagram 세로 | 중앙 기준 크롭 → 1080×1350 |
| 16:9 | YouTube (원본) | 스케일만 → 1920×1080 |

**아키텍처:**
- `video_resizes` 테이블 (original_video_id, aspect_ratio, file_url, status)
- `VideoResizeUseCase` → `FfmpegResizeService` (인프라)
- 비동기 이벤트 기반 처리 (기존 VideoPostProcess 패턴)
- 리사이즈된 영상은 별도 파일로 S3/MinIO 저장
- AI 크레딧: **3 credits / 변환**

---

## 2. 트렌드 모니터링 (다중 소스)

**개념:** YouTube API + Google Trends + 내부 데이터를 종합하여 실시간 트렌드 대시보드 제공

**데이터 파이프라인:**
```
YouTube Data API (인기 동영상/검색 트렌드)
   ↓
Google Trends (키워드 관심도)
   ↓
내부 analytics_daily (자체 성과 패턴)
   ↓
AI 종합 분석 → 카테고리별 트렌드 리포트 + 콘텐츠 제안
```

**DB:**
- `trends` (id, category, keyword, score, source, platform, date, created_at)
- `trend_alerts` (id, user_id, keyword, threshold, enabled, created_at)

**스케줄러:** 매일 1회 트렌드 갱신 + 사용자 알림
**AI 크레딧:** **5 credits / 분석 요청**

---

## 3. 팬/오디언스 CRM (프로필 + 세그먼트)

**개념:** 댓글·상호작용 데이터로 팬 프로필 자동 생성, 세그먼트 분류, 참여도 점수 산정

**핵심 모델:**
- `audience_profiles` (id, user_id, author_name, platform, avatar_url, engagement_score, tags JSONB, total_interactions, positive_ratio, first_seen_at, last_seen_at, created_at, updated_at)
- `audience_segments` (id, user_id, name, description, conditions JSONB, auto_update, created_at)
- `audience_profile_segments` (profile_id, segment_id)

**참여도 점수 산정:**
```
score = (댓글 빈도 × 0.4) + (최근성 × 0.3) + (감정 긍정률 × 0.3)
```

**자동 태그:** AI 감정분석 기반 (열성팬/일반/비판적 등)
**세그먼트 조건 예시:** `{"field": "engagement_score", "op": ">=", "value": 80, "and": {"field": "last_seen_days", "op": "<=", "value": 30}}`

---

## 4. 다국어 콘텐츠 자동화 (메타데이터 + 자막)

**개념:** AI(Claude)가 제목/설명/해시태그 + 자막 SRT를 다국어로 번역

**지원 언어:** ko, en, ja, zh, es, fr, de, pt

**DB:**
- `video_translations` (id, video_id, language, title, description, tags JSONB, subtitle_content TEXT, status, created_at, updated_at)

**워크플로우:**
1. 사용자가 대상 언어 선택 → 번역 요청
2. AI가 메타데이터 + 자막 번역 (플랫폼 특성 반영)
3. 사용자 검토 후 플랫폼별 적용

**AI 크레딧:** **3 credits / 언어 / 번역**

---

## 5. 브랜드 딜/스폰서십 (딜 트래커 + 미디어키트)

**개념:** 스폰서십 딜 관리 + 크리에이터 미디어키트 PDF 자동 생성

**딜 관리:**
- `brand_deals` (id, user_id, brand_name, brand_logo_url, deal_type, amount, currency, status, start_date, end_date, deliverables JSONB, notes, contact_name, contact_email, created_at, updated_at)
- 상태 흐름: PROPOSAL → NEGOTIATION → IN_PROGRESS → COMPLETED / CANCELLED
- 대시보드: 총 매출, 진행중 딜, 월별 추이

**미디어키트:**
- 채널 통계, 상위 영상, 오디언스 인구통계, 참여율, 가격표를 포함한 PDF
- 기존 jsPDF/html2canvas 인프라 활용
- `media_kits` (id, user_id, generated_at, pdf_url, stats_snapshot JSONB)

---

## 기술 스택 활용

- **백엔드:** Spring Boot 4 + Kotlin + jOOQ (기존 패턴)
- **영상 처리:** FFmpeg (기존 인프라)
- **AI:** Spring AI ChatClient (Claude) (기존 인프라)
- **비동기:** Spring Events + Virtual Threads (기존 패턴)
- **프론트엔드:** Vue 3 + Pinia + Tailwind CSS (기존 패턴)
- **PDF:** jsPDF + html2canvas (기존 인프라)
- **외부 API:** YouTube Data API v3, Google Trends (신규)
