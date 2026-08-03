# onGo 리디자인 — 병렬 작업 계약서

원본 스펙: `design_handoff_ongo_redesign/README.md` (권위 있는 문서)
프로토타입: `design_handoff_ongo_redesign/onGo Redesign.dc.html` (레이아웃·문구 확인용)

**여러 작업자가 같은 워크트리에서 동시에 일한다. 자기 소유 파일만 건드린다.**

## 이미 끝난 것 (건드리지 말 것)

토큰 계층은 완료되어 커밋 대상이다. **아래 파일은 누구도 수정하지 마라.**

```
frontend/src/assets/tokens.css      다크 팔레트를 리디자인 값으로 교체 완료
frontend/src/assets/main.css        .card/.btn-*/.input-field 밀도·색 교체 완료
frontend/tailwind.config.js         surface/line/content/accent/platform 유틸리티 추가 완료
frontend/index.html                 JetBrains Mono 로드 완료
```

## 쓸 수 있는 유틸리티 (hex 직접 사용 금지)

토큰은 전부 CSS 변수 기반이라 라이트/다크가 자동으로 뒤집힌다. **시안의 hex 를 그대로 박지 말고
아래 이름을 쓸 것.**

| 시안 값 | 유틸리티 |
|---|---|
| `#101120` 앱 바깥 배경 | `bg-surface-base` |
| `#181a27` 컨테이너 | `bg-surface` |
| `#1b1e2c` 카드 | `bg-surface-card` |
| `#171927` 입력·서브패널 | `bg-surface-input` |
| `#1f2233` hover·선택 행 | `bg-surface-raised` |
| `#14161f` 레일 | `bg-surface-rail` |
| `#1d2030` 레일 hover | `bg-surface-rail-raised` |
| `#23263c` 보더(강) | `border-line` |
| `#202335` 행 구분 | `border-line-row` |
| `#2e3250` 컨트롤 보더 | `border-line-control` |
| `#262940` 보조 버튼 보더 | `border-line-soft` |
| `#3a3f61` hover 보더 | `border-line-hover` |
| `#e8e9f2` 본문 | `text-content` |
| `#8f93b3` 보조 | `text-content-secondary` |
| `#6c7093` 약함 | `text-content-tertiary` |
| `#575b7c` 최약 | `text-content-quaternary` |
| accent (마젠타) | `bg-accent` `text-accent` `border-accent` |
| accent dim | `bg-accent-dim` |
| accent 위 글자 | `text-accent-on` |
| warn 점 | `text-warn` `bg-warn` |
| bad 점 | `text-bad` `bg-bad` |

상태 배지는 기존 시맨틱 유틸리티를 쓴다 — `bg-success-subtle text-success-strong`,
`warning`, `error`, `muted` 동일 패턴. 시안의 pill 색쌍이 이미 여기에 매핑돼 있다.

플랫폼 칩: `bg-platform-yt-bg text-platform-yt-fg` (yt/ig/tt/fb/nv/th).

모노 서체는 `font-mono` (JetBrains Mono). KPI 값·시간·타임스탬프·대문자 라벨에 쓴다.

## 공통 규칙

- 고밀도다. 테이블 **행 높이 44~48px**, 컨트롤 32~36px.
- radius: 배지 4~5 · 칩 6~7 · 컨트롤 8~9 · 카드 11~12.
- **그림자 금지.** 깊이는 보더와 표면 명도로 표현한다.
- 트랜지션 120ms ease-out.
- 아이콘은 이미 설치된 `@heroicons/vue` 를 쓴다. 시안의 텍스트 글리프(`● ▲ ■ ⋯`)를 그대로 쓰지 마라.
- 썸네일 자리표시자: `repeating-linear-gradient(135deg,#262a41 0 6px,#2d3149 6px 12px)`
  (이건 hex 예외 — 자리표시자 패턴이라 토큰화하지 않았다)
- i18n 은 `locales/ko/common.json` 과 `en/common.json` **양쪽 동시** 갱신. 키는 `redesign.*` 아래.
- 미사용 import 금지 (TS6133 빌드 에러).
- 주석은 한국어.
- **데이터는 실제 스토어·API 를 쓴다.** 시안의 목업 숫자를 하드코딩하지 마라. 아직 API 가 없는
  지표는 스토어에 자리만 만들고 로딩/빈 상태를 처리한다.
- 반응형: ≥1440 3열 유지 / 1024~1440 인박스 필터 열 접기·컴포저 우측 320px / <1024 단일 열 + 하단 탭.

## 라우트

새 화면은 아래 경로에 **신규로** 만든다. 기존 뷰와 라우트는 그대로 둔다(URL 로 계속 접근 가능).

```
/today        오늘        ShortsTodayView 아님 — RedesignTodayView.vue
/compose      새 업로드
/inbox-v2     인박스
/calendar-v2  캘린더
/performance  성과
/channels-v2  채널
/settings-v2  설정
```

기존 `/inbox`, `/calendar`, `/channels`, `/settings` 는 살아 있으므로 새 화면은 `-v2` 를 붙인다.
`/today`, `/compose`, `/performance` 는 비어 있어 그대로 쓴다.

## 파일 소유권

### 작업자 A — 셸과 오늘 (내가 직접 한다)

```
frontend/src/components/layout/SideNav.vue        216px 레일로 재작성
frontend/src/components/layout/TopBar.vue         56px 상단바로 재작성
frontend/src/components/layout/AppLayout.vue      프레임 조정
frontend/src/components/redesign/                 공용 프리미티브 (아래 참고)
frontend/src/views/redesign/TodayView.vue
frontend/src/router/index.ts                      라우트 7개 추가
```

작업자 A 가 먼저 만드는 공용 컴포넌트 — **B·C 는 이것을 재사용한다.**

| 컴포넌트 | 용도 |
|---|---|
| `redesign/StatusPill.vue` | 상태 배지. `variant: success/warning/error/muted` |
| `redesign/PlatformChip.vue` | 플랫폼 칩. `platform: YT/IG/TT/FB/NV/TH`, `size: sm/md` |
| `redesign/KpiCard.vue` | KPI 카드. 라벨/값/델타/노트 |
| `redesign/ThumbPlaceholder.vue` | 썸네일 자리표시자 + 길이 배지 |
| `redesign/SectionCard.vue` | 카드 프레임 + 헤더(제목/우측 액션 슬롯) |

### 작업자 B (kimi) — 인박스 · 캘린더

```
frontend/src/views/redesign/InboxView.vue
frontend/src/views/redesign/CalendarView.vue
frontend/src/api/redesign/*.ts        필요하면
frontend/src/stores/redesignInbox.ts
frontend/src/stores/redesignCalendar.ts
```

- **인박스**: README 3절. 3열(`178px / minmax(0,340px) / minmax(0,1fr)`).
  필터 열 + 스레드 목록 + 상세. 전송 후 자동으로 다음 미답변으로 이동, `J`/`K` 목록 이동,
  `⌘↵` 전송. 기존 댓글 API(`@/api/comment` 등)를 최대한 재사용하라.
- **캘린더**: README 5절. 주간 7열 그리드, 예약 블록 드래그로 시간·요일 변경,
  빈 슬롯 클릭 시 `/compose` 로 해당 시각 프리필하며 이동.

### 작업자 C (codex) — 성과 · 채널 · 설정

```
frontend/src/views/redesign/PerformanceView.vue
frontend/src/views/redesign/ChannelsView.vue
frontend/src/views/redesign/SettingsView.vue
frontend/src/stores/redesignPerformance.ts
```

- **성과**: README 4절. 기간 세그먼트(7/30/90일) + KPI 4장 + 일별 막대 차트(높이 168px,
  상위 10% 는 accent) + 하단 2열(상위 영상 테이블 / 인사이트 카드 3장). CSV 내보내기.
- **채널**: README 6절. 최상단 오류 배너 + 채널 카드 그리드(`repeat(auto-fill,minmax(268px,1fr))`)
  + 점선 `+ 새 플랫폼 연결` 카드 + 발행 규칙 토글 테이블.
- **설정**: README 7절. 좌측 서브내비 194px + 본문 최대 880px. 자동화 토글 6행 + 하단 3카드.

## 하지 말 것

- 위 "이미 끝난 것" 4개 파일 수정
- 기존 뷰·라우트 삭제나 변경 (새로 얹기만 한다)
- `design_handoff_ongo_redesign/` 안의 파일 수정
- 로그인 화면 수정 — **리디자인 범위 밖이다** (사용자 요청으로 기존 디자인 유지)
- `git commit`
- 남의 소유 파일 수정. 버그를 발견하면 고치지 말고 보고하라.

## 검증

`cd frontend && npm run build` (vue-tsc 타입 체크 포함) 통과.
끝나면 파일별 변경 요약, 빌드 결과, 임의로 정한 부분을 보고하라.
