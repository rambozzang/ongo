# onGo 킬러 기능 기획서 v2.0 — 글로벌 경쟁력 확보 전략

> 작성일: 2026-04-06
> 상태: 기획 검토 대기
> 전제: 영상 파일 자체를 호스팅/편집하지 않음 (배포 + AI 최적화 + 분석 허브)

---

## 현재 상태 분석

### 라이브 기능 (사이드바 노출, 22개)
| 카테고리 | 기능 | DB 테이블 | 상태 |
|---------|------|----------|------|
| 콘텐츠 | 대시보드, 업로드, 영상관리, AI 도구, 아이디어, 템플릿, 브랜드키트 | V1~V7 | LIVE |
| 스케줄링 | 예약관리, 캘린더 | V1, V20 | LIVE |
| 분석 | 통합분석, 수익분석 | V1, V4 | LIVE |
| 소통 | 댓글관리, 소셜인박스, 알림센터 | V3, V5, V17 | LIVE |
| 운영 | 채널관리, 활동로그 | V1, V5 | LIVE |
| 기타 | 매뉴얼, 구독, 설정, 관리자 | V1, V34 | LIVE |

### 라우터만 등록 (사이드바 미노출, 15개)
abtest, competitor, goals, recycling, assets, webhooks, trends, audience, brand-deals, linkbio, team, video-compare, video-detail, automation, login/onboarding/callback

### 고아 뷰 (라우터 미등록, ~75개) → 삭제 대상
DB 테이블(V22~V38)은 존재하나 프론트엔드에서 접근 불가. 전부 삭제.

---

## 경쟁사 분석 기반 신규 기능 TOP 10

### Phase 1: 즉시 구현 가능 (기존 인프라 활용, 2~3주)

#### 1. AI 최적 업로드 시간 추천
> 기존 스케줄링에 "언제" 올려야 하는지 AI 추천 추가

- **경쟁사**: Hootsuite (AI Best Time), Buffer, Later
- **핵심**: 채널별 과거 성과 데이터 + 시청자 활동 패턴 분석 → 플랫폼별 최적 시간대 추천
- **구현**: 기존 `analytics_daily` + `schedules` 데이터 활용, AI 분석만 추가
- **UI**: 스케줄 생성 시 "AI 추천 시간" 버튼 (CalendarView/ScheduleView 내 통합)
- **수익**: AI 크레딧 2개 소모
- **복잡도**: LOW
- **API**: `POST /api/v1/schedules/optimal-time` → `{ platform, recommendations: [{ time, score, reason }] }`
- **활용 테이블**: `analytics_daily`, `schedules`, `optimal_time_slots`(V27)

#### 2. AI 콘텐츠 캘린더 자동 생성
> "이번 주 뭘 올릴지" 전략적 캘린더를 AI가 자동 제안

- **경쟁사**: Hootsuite (AI Content Calendar), Later, Planoly
- **핵심**: 채널 카테고리 + 업로드 빈도 + 트렌드 데이터 → 주간/월간 콘텐츠 캘린더 자동 제안
- **구현**: 기존 `ai_content_calendars`(V32) + 트렌드 데이터 결합
- **UI**: CalendarView에 "AI 캘린더 생성" 버튼 추가
- **수익**: AI 크레딧 5개 소모
- **복잡도**: LOW
- **API**: `POST /api/v1/calendar/ai-generate` → `{ weekPlan: [{ date, platform, contentType, topic, optimalTime }] }`

#### 3. 탑 퍼포머 메타데이터 리라이터
> 성과 좋은 영상의 제목/설명/태그를 트렌드에 맞게 AI가 재작성 제안

- **경쟁사**: Hootsuite (OwlyWriter AI), Buffer AI
- **핵심**: 과거 고성과 영상 자동 식별 → 현재 트렌드에 맞춰 메타데이터 리라이팅 → 재업로드/업데이트 제안
- **구현**: 기존 AI 메타데이터 최적화 엔진 + `analytics_daily` 고성과 영상 필터링
- **UI**: VideosView에서 고성과 영상에 "AI 리라이트" 배지 표시
- **수익**: AI 크레딧 3개 소모
- **복잡도**: LOW
- **API**: `POST /api/v1/videos/{id}/rewrite-meta` → `{ original, suggested, expectedImpact }`

---

### Phase 2: 핵심 차별화 기능 (3~5주)

#### 4. A/B 테스팅 (썸네일/제목)
> 동일 영상에 2개 썸네일/제목 교대 노출 → CTR 기반 승자 자동 선정

- **경쟁사**: TubeBuddy (핵심 유료 기능), VidIQ
- **핵심**: 썸네일 A/B를 설정하면 일정 주기로 교체 → 일정 기간 후 CTR 기반 95% 신뢰도에서 승자 자동 선정
- **락인**: 크리에이터의 핵심 성장 도구 — TubeBuddy에서 가장 인기 유료 기능
- **구현**: 기존 `ab_tests`(V4) + `ab_test_variants`(V4) 테이블 활용, YouTube API thumbnails.set() 연동
- **UI**: 기존 AbTestView 강화 (현재 라우터에 등록되어 있으나 사이드바 미노출)
- **수익**: Pro/Business 티어 전용
- **복잡도**: MEDIUM
- **API**:
  - `POST /api/v1/ab-tests` → 테스트 생성
  - `POST /api/v1/ab-tests/{id}/switch` → 변형 교체 실행
  - `GET /api/v1/ab-tests/{id}/results` → 통계 결과
- **일정**: YouTube Data API v3 thumbnails.set() + analytics 연동 필요

#### 5. 경쟁 채널 추적 & 벤치마킹
> 경쟁 크리에이터 채널을 등록하면 신규 업로드, 성과, 키워드를 자동 추적

- **경쟁사**: VidIQ (Competitors Tool), Hootsuite (최대 5개 추적)
- **핵심**: 경쟁 채널 등록 → 신규 업로드 알림, 성과 비교, 사용 키워드 분석, 성장률 비교
- **락인**: 경쟁사 데이터가 쌓일수록 떠나기 어려움 (데이터 락인)
- **구현**: 기존 `competitors`(V4) + `competitor_profiles`(V27) + `competitor_reports`(V27) 활용
- **UI**: CompetitorView 강화 + 사이드바 노출
- **수익**: Free 2채널 / Starter 5채널 / Pro 15채널 / Business 무제한
- **복잡도**: MEDIUM
- **API**:
  - `POST /api/v1/competitors` → 경쟁 채널 등록
  - `GET /api/v1/competitors/{id}/report` → 비교 리포트
  - `GET /api/v1/competitors/benchmark` → 벤치마크 대시보드

#### 6. AI 콘텐츠 아이디어 제너레이터 (Daily Ideas)
> 채널 분석 + 트렌드 기반 매일 개인화된 영상 아이디어 자동 생성

- **경쟁사**: VidIQ (Daily Ideas — 핵심 기능), TubeBuddy (Keyword Explorer)
- **핵심**: 채널 카테고리 + 최근 성과 + 실시간 트렌드 → 매일 10개 맞춤 아이디어 생성
- **락인**: 매일 앱을 여는 습관 형성 (DAU 극대화)
- **구현**: 기존 IdeasView + `ideas`(V7) 테이블에 AI 자동 생성 기능 추가
- **UI**: IdeasView에 "오늘의 AI 아이디어" 섹션 추가 + 대시보드 위젯
- **수익**: Free 3개/일, Pro 10개/일, Business 50개/일 (AI 크레딧 소모)
- **복잡도**: MEDIUM
- **API**: `POST /api/v1/ideas/ai-generate` → `{ ideas: [{ title, description, keywords, estimatedViews, difficulty }] }`

#### 7. 콘텐츠 리퍼포징 어시스턴트 (하이라이트 추출)
> 긴 영상 URL → AI가 숏폼 클립 후보 구간(타임스탬프) + 추천 제목/설명 제안

- **경쟁사**: OpusClip, TubeBuddy (Suggested Shorts), Descript
- **핵심**: 영상 파일 처리 없이 Whisper STT → 트랜스크립트 분석 → 하이라이트 구간 추출
- **구현**: 기존 Whisper STT 인프라 + AI 분석, `repurpose_jobs`(V26) + `content_clips`(V24) 활용
- **UI**: 영상 상세에서 "숏폼 추출" 버튼 → 추천 구간 리스트
- **수익**: AI 크레딧 10개 소모 (STT + 분석)
- **복잡도**: MEDIUM
- **제약**: 영상 편집/자르기는 하지 않음 — "어디를 잘라야 하는지"만 알려줌
- **API**: `POST /api/v1/videos/{id}/repurpose` → `{ clips: [{ startTime, endTime, title, description, viralScore }] }`

---

### Phase 3: 고부가가치 프리미엄 기능 (5~8주)

#### 8. AI 미디어 킷 & 레이트 카드 자동 생성
> 채널 데이터 기반 전문 미디어 킷 PDF + 적정 광고 단가 AI 산출

- **경쟁사**: SponsorRadar (1-click 미디어 킷), InfluenceFlow, CreatorIQ
- **핵심**: 구독자, 조회수, 인구통계, 참여율 → 미디어 킷 PDF 자동 생성 + 적정 단가 계산
- **락인**: 수익화 직결 — 미디어 킷 보유 크리에이터가 브랜드 문의 40% 더 받음
- **구현**: 기존 분석 데이터 + `media_kits`(V31) + PDF 렌더링 라이브러리
- **UI**: 새 MediaKitView (사이드바 "운영" 그룹에 추가)
- **수익**: Pro/Business 전용
- **복잡도**: MEDIUM
- **API**:
  - `POST /api/v1/media-kit/generate` → AI 미디어 킷 생성
  - `GET /api/v1/media-kit/download` → PDF 다운로드
  - `POST /api/v1/media-kit/rate-card` → AI 적정 단가 산출

#### 9. 채널 오디트 & 월간 성장 리포트
> AI가 채널 전체를 분석해서 강점/약점/구체적 액션 아이템 제공

- **경쟁사**: VidIQ (Channel Audit), Sprout Social
- **핵심**: 월간 자동 리포트 — 아웃라이어 영상 분석, 성장 병목 진단, 개선 액션 아이템
- **구현**: 기존 `performance_reports`(V28) + `weekly_reports`(V32) + AI 분석
- **UI**: 대시보드에 "이달의 채널 리포트" 카드 + PDF 내보내기
- **수익**: AI 크레딧 15개 (월간 자동 생성은 Pro 이상)
- **복잡도**: MEDIUM
- **API**: `POST /api/v1/reports/channel-audit` → `{ strengths, weaknesses, actions, outlierVideos, growthForecast }`

#### 10. 크로스 플랫폼 해시태그 & 키워드 리서치
> YouTube/TikTok/Instagram/Naver 각 플랫폼의 키워드 트렌드를 통합 분석

- **경쟁사**: VidIQ (Keyword Inspector), TubeBuddy (Keyword Explorer)
- **핵심**: "이 키워드는 TikTok에서 뜨지만 YouTube에서는 블루오션" 같은 크로스 플랫폼 인사이트
- **락인**: onGo만의 멀티 플랫폼 포지셔닝 최대 차별점
- **구현**: 기존 `hashtag_performances`(V27) + `hashtag_groups`(V27) + 플랫폼 API 트렌드 데이터
- **UI**: TrendView 강화 + 해시태그 분석 탭 추가
- **수익**: AI 크레딧 3개 소모
- **복잡도**: HIGH (플랫폼별 트렌드 데이터 수집 파이프라인)
- **API**: `POST /api/v1/keywords/research` → `{ keyword, platforms: [{ platform, searchVolume, competition, trend, opportunity }] }`

---

## 장기 로드맵 (Phase 4+, 구현 검토 필요)

| 기능 | 복잡도 | 수익 잠재력 | 비고 |
|------|--------|-----------|------|
| 브랜드 딜 마켓플레이스 | HIGH | 매우 높음 | 양면 마켓 구축, 거래 수수료 20~30% |
| 소셜 리스닝 & 멘션 모니터링 | HIGH | 높음 | 외부 크롤링 필요 |
| 통합 소셜 인박스 (DM 포함) | HIGH | 높음 | 플랫폼 DM API 제한적 |
| 링크인바이오 & 크리에이터 스토어 | MEDIUM | 중간~높음 | 결제 연동 |
| 팀 협업 & RBAC 확장 | MEDIUM | 높음 | Business 티어 핵심 |

---

## 구현 우선순위 매트릭스

```
수익 잠재력
  높 │  ④A/B테스트   ⑧미디어킷   ⑥아이디어생성
     │  ⑤경쟁채널추적  ⑩키워드리서치 ⑦리퍼포징
     │
  중 │  ①최적시간     ②AI캘린더    ⑨채널오디트
     │  ③메타리라이트
     │
  낮 │
     └──────────────────────────────────
       낮(LOW)      중(MEDIUM)     높(HIGH)    복잡도
```

**추천 구현 순서:**
1. Phase 1 (1~3번) → 기존 인프라만으로 2~3주 내 완료 가능, 즉시 가치 제공
2. Phase 2 (4~7번) → 핵심 차별화, 유료 전환 핵심 기능
3. Phase 3 (8~10번) → 프리미엄 기능, Pro/Business 티어 가치 강화

---

## 기존 라우터 등록 뷰 중 사이드바 승격 검토

현재 라우터에 있지만 사이드바에 없는 기능 중, 위 기획과 연계하여 승격할 후보:

| 뷰 | 현재 상태 | 승격 여부 | 이유 |
|----|----------|----------|------|
| CompetitorView | 라우터만 | **승격** (Phase 2 #5) | 경쟁 채널 추적 기능의 메인 뷰 |
| AbTestView | 라우터만 | **승격** (Phase 2 #4) | A/B 테스팅 기능의 메인 뷰 |
| TrendView | 라우터만 | **승격** (Phase 3 #10) | 키워드 리서치 통합 |
| GoalsView | 라우터만 | 유지 | 내부 링크로 접근 가능 |
| RecyclingView | 라우터만 | 유지 | 내부 링크로 접근 가능 |
| AudienceView | 라우터만 | 검토 | CRM 기능 확장 시 승격 |
| BrandDealView | 라우터만 | 검토 | Phase 4 마켓플레이스 구현 시 |
| LinkBioView | 라우터만 | 검토 | Phase 4 크리에이터 스토어 구현 시 |
| TeamView | 라우터만 | 검토 | Phase 4 RBAC 확장 시 |

---

## 삭제 대상 고아 뷰 (~75개)

라우터 미등록 + 사이드바 미노출 = 사용자 접근 불가.
프론트엔드 뷰, 스토어, API 파일을 삭제합니다.
백엔드 컨트롤러와 DB 마이그레이션은 유지 (향후 재구현 시 활용).

---

## 경쟁사 대비 포지셔닝

```
                    AI 기능 깊이
                        ↑
                        │
          VidIQ ●       │       ● onGo (목표)
                        │
     TubeBuddy ●        │
                        │
                ────────┼────────→ 멀티 플랫폼 지원
                        │
        Later ●         │       ● Hootsuite
                        │
         Planoly ●      │
```

**onGo의 핵심 차별점:**
1. **멀티 플랫폼 동시 업로드** — 경쟁사 대부분 분석/관리만 (업로드는 각 플랫폼 직접)
2. **AI 네이티브** — 모든 기능에 AI 통합 (VidIQ/TubeBuddy는 YouTube 중심)
3. **한국 시장 특화** — 네이버 클립 지원, 한국어 AI
4. **올인원** — 업로드 + 분석 + AI + 스케줄링 + 댓글관리를 하나의 도구로
