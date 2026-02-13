# onGo Frontend Progress Tracker

> Last updated: 2026-02-09

## Completed Batches

### Batch 1-11: Core Views
- Dashboard, Upload, Schedule, Calendar, Videos, VideoDetail, Analytics, VideoCompare
- Comments, AI, Channels, Revenue, Subscription, Settings, Login, Onboarding

### Batch 12: Social & Collaboration
- IdeasView (아이디어 칸반 보드)
- TeamView (팀 관리)
- AutomationView (자동화 규칙)
- InboxView (소셜 통합 인박스)
- LinkBioView (링크인바이오 빌더)

### Batch 13: Analytics & Strategy
- TemplatesView (콘텐츠 템플릿 라이브러리)
- ABTestView (A/B 테스트 관리)
- CompetitorView (경쟁사 분석)
- BrandKitView (브랜드 키트)
- GoalsView (목표/마일스톤 트래커)

### Batch 14: Operations & Infra
- NotificationsView (알림 센터)
- RecyclingView (콘텐츠 재활용)
- AssetsView (에셋 라이브러리)
- WebhooksView (웹훅 관리)
- ActivityLogView (활동 로그)

### Batch 15: Phase 1 Enhancement Features
- Bulk Upload Queue Manager (벌크 업로드 큐 - 일시정지/재개/재시도/우선순위)
  - src/types/uploadQueue.ts, src/stores/bulkUploadQueue.ts
  - src/components/upload/BulkUploadQueueManager.vue, UploadQueueItem.vue, BulkUploadDropzone.vue
- CSV/Excel Bulk Scheduling (CSV 일괄 스케줄링 - 파싱/검증/미리보기)
  - src/types/csvImport.ts, src/stores/csvImport.ts
  - src/components/schedule/CsvImportModal.vue, CsvPreviewTable.vue, CsvTemplateInfo.vue
- Watermark Settings (워터마크 - 이미지/텍스트, 9-포지션, 프리셋)
  - src/types/watermark.ts, src/stores/watermark.ts
  - src/components/watermark/WatermarkEditor.vue, WatermarkPreview.vue, WatermarkPositionGrid.vue, WatermarkPresetManager.vue
- Recurring Schedule Builder (반복 예약 - 매일/주간/격주/월간 패턴)
  - src/types/recurringSchedule.ts, src/stores/recurringSchedule.ts
  - src/components/schedule/RecurringRuleList.vue, RecurringRuleFormModal.vue, ContentQueuePanel.vue, VideoAssignModal.vue
- Publish Approval Workflow (게시 승인 - 요청/검토/승인/반려/수정요청)
  - src/types/approval.ts, src/stores/approval.ts
  - src/components/approval/ApprovalDashboard.vue, ApprovalRequestCard.vue, ApprovalDetailModal.vue, ApprovalTimeline.vue, SubmitApprovalModal.vue

### Bug Fixes
- Chart.js HEX color parsing in TrendChart.vue (hexToRgb function)
- Chart.js onBeforeUnmount cleanup in TrendChart.vue & PlatformPieChart.vue
- Dashboard error handling: Promise.all -> Promise.allSettled in dashboard store

---

## Current Stats
- Views: 31 | Components: 174 | Stores: 42 | Types: 30
- Total .vue: 205 | Total .ts: 108 | Lines of code: 62,492
- Build: 139 entries, 1801 KiB, 0 errors

---

## Remaining Roadmap (Not Yet Built)

Reference: `/Users/bumkyuchun/work/app/ongo/plans/03-feature-roadmap.md`

### Batch 16 Candidates: Phase 1 Remaining
1. **벌크 액션 (Bulk Actions)** - 영상 목록에서 다중 선택 후 일괄 삭제/재업로드/태그변경/카테고리변경
   - P1, S난이도, ★★☆ 임팩트
   - VideosView에 체크박스 선택 + 액션 바 추가
2. **에버그린 콘텐츠 태그** - 상록수 콘텐츠 태깅하여 재활용 큐 자동 추가
   - P2, S난이도, ★☆☆ 임팩트
   - RecyclingView에 에버그린 태그 토글 추가
3. **데스크톱 푸시 알림** - Web Push Notification 설정 UI
   - P1, S난이도, ★★☆ 임팩트
   - NotificationsView 설정 패널에 Push 구독 UI
4. **인트로/아웃트로 자동 삽입 설정** - 미리 설정한 인트로/아웃트로 영상 관리 UI
   - P2, M난이도, ★☆☆ 임팩트
5. **RBAC 역할 관리 강화** - 역할별 권한 매트릭스 설정 UI
   - P0, M난이도, ★★★ 임팩트
   - TeamView에 역할 권한 설정 패널 추가

### Batch 17 Candidates: Phase 2 Core
1. **AI 영상 리사이징 UI** - 16:9→9:16 등 비율 변환, 미리보기, 수동 크롭 조정
   - P0, L난이도, ★★★ 임팩트
   - 새 컴포넌트 세트: VideoResizer, CropEditor, AspectRatioSelector
2. **자동 자막 생성/번인 UI** - STT 결과 편집, 자막 스타일 프리셋, 타임스탬프 편집
   - P1, L난이도, ★★★ 임팩트
   - 새 컴포넌트 세트: SubtitleEditor, SubtitleTimeline, SubtitleStylePresets
3. **썸네일 AI 생성 UI** - 프레임 선택, 텍스트 오버레이, 생성 결과 미리보기
   - P2, L난이도, ★★☆ 임팩트
4. **AI 성과 예측 UI** - 예상 조회수, 최적 업로드 시간, 제목 A/B 점수
   - P1, M난이도, ★★☆ 임팩트
5. **자동 성과 리포트 PDF** - 리포트 설정, 미리보기, 이메일 발송 설정
   - P1, M난이도, ★★☆ 임팩트

### Batch 18 Candidates: Phase 2 Agency & Library
1. **멀티 워크스페이스** - 워크스페이스 스위처, 생성/관리, 에이전시 요약 대시보드
   - P0, L난이도, ★★★ 임팩트
2. **클라이언트 포털** - 공유 링크 생성, 읽기전용 대시보드, 승인/피드백
   - P1, M난이도, ★★☆ 임팩트
3. **에이전시 대시보드** - 전체 워크스페이스 성과 요약, 순위, 이상 감지
   - P1, M난이도, ★★☆ 임팩트
4. **AI 자동 태깅 라이브러리** - 에셋 자동 분류, 스마트 컬렉션, 고급 검색
   - P1, M난이도, ★★☆ 임팩트
   - AssetsView 강화
5. **플랫폼 확장 UI (Facebook/Twitter)** - 채널 연동 설정, 게시 옵션
   - P1-P2, M난이도, ★★☆-★☆☆ 임팩트

### Batch 19 Candidates: Phase 3 Growth
1. **AI 콘텐츠 캘린더 자동 생성** - AI 기반 2-4주 캘린더, 트렌드 반영, 시즌 추천
   - P0, L난이도, ★★★ 임팩트
2. **크리에이터 마켓플레이스** - 캠페인 관리, 크리에이터 검색/매칭, 협찬 관리
   - P1, XL난이도, ★★★ 임팩트
3. **크리에이터 포트폴리오 페이지** - 공개 프로필, 하이라이트, 미디어 킷
   - P1, M난이도, ★★☆ 임팩트
4. **커머스 연동 (쿠팡/스마트스토어)** - 상품 링크 관리, 클릭/수익 추적
   - P1-P2, M난이도, ★★☆ 임팩트
5. **통합 수익 대시보드** - 애드센스+쿠팡+스마트스토어+협찬 통합 수익 뷰
   - P1, L난이도, ★★★ 임팩트

### Batch 20 Candidates: Phase 3 Advanced
1. **트렌드 알림 시스템** - 실시간 키워드 트렌드 감지, 알림 설정
   - P1, M난이도, ★★☆ 임팩트
2. **커스텀 워크플로우 빌더** - 시각적 워크플로우 설계 (기획→촬영→편집→검수→게시)
   - P2, XL난이도, ★★☆ 임팩트
3. **화이트라벨 설정** - 커스텀 도메인, 로고/색상, 이메일 브랜딩
   - P1, L난이도, ★★★ 임팩트
4. **팀 성과 분석** - 멤버별 기여도, 업로드 수, 응답 속도 통계
   - P3, M난이도, ★☆☆ 임팩트
5. **카카오톡 채널/네이버 블로그 연동** - 추가 플랫폼 연동 설정
   - P1-P2, M난이도, ★★☆ 임팩트

---

## Development Pattern

Each batch follows this pattern:
1. 5 features per batch, built in parallel using agents
2. Each feature creates: types (.ts) + store (.ts) + components (.vue)
3. After all agents complete, fix any TypeScript errors
4. Run `npm run build` to verify 0 errors
5. Integration: add routes to router/index.ts + menu items to SideNav.vue (only for new views)

### Agent Instructions Template
- Tech: Vue 3 + TypeScript + Tailwind CSS + Pinia
- `<script setup lang="ts">` MUST have `</script>` before `<template>`
- Dark mode support with `dark:` prefix
- Korean text for all labels
- @heroicons/vue/24/outline for icons
- defineProps/defineEmits with TypeScript generics
- Do NOT modify existing files unless explicitly needed
