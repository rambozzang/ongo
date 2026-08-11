# onGo 프론트엔드 정보구조 교차검토

2026-08-11 · 공유 워크트리(main) · 기능 삭제 없음 · backend 미수정 · 커밋하지 않음

읽은 파일: `RedesignRail.vue`, `RedesignLayout.vue`, `views/redesign/TodayView.vue`,
`composables/useNavigation.ts`, `useNavigation.test.ts`, `SideNav.vue`, `MobileMenuSheet.vue`,
`MobileBottomNav.vue`, `RedesignTopBar.vue`, `router/index.ts`.

## 요약 수치

| 항목 | 값 |
|---|---|
| 라우트 총계 | **69** |
| 레일 노출 목적지 | **35** (그룹 8 + 서브그룹 2 + 하단 4) |
| 모바일 하단 바 | **4** |
| 핵심 흐름 화면 | **4** (오늘 → 작성 → 캘린더/채널 → 성과/인박스) |
| v1/v2 중복 쌍 | **4** |

같은 제품에 내비게이션 표면이 3개이고 각각 큐레이션이 다르다. 레일 35개, 모바일 하단 4개,
모바일 시트 35개(접힘 지원). **모바일은 이미 핵심 4개로 좁혀져 있는데 데스크톱만 전부 편다.**

## (1) 사용자가 길을 잃는 지점

**L1. 레일이 35개를 항상 펼친 채 쌓는다.**
`SideNav.vue:109-119` 와 `MobileMenuSheet.vue:93-104` 는 서브그룹 접기를 구현하는데
`RedesignRail.vue` 만 빠져 있다. `useNavigation` 이 `isSubGroupExpanded`/`toggleSubGroup` 과
localStorage 영속·현재 라우트 자동 펼침까지 이미 제공하는데 레일이 쓰지 않는다.
리디자인 셸이 **모든 인증 화면의 유일한 셸**(`RedesignLayout`)이 된 뒤로 이게 기본 경험이 됐다.
→ Codex가 현재 이 파일에서 수정 중(핵심 작업 고정 + 나머지 접기).

**L2. 핵심 흐름이 첫 클릭에서 워크스페이스를 벗어난다.**
`TodayView.openItem()` 이 `/videos/{id}` 로 보낸다. 오늘 화면(리디자인)에서 발행 예정 항목을
누르면 레거시 영상 상세로 나간다. "오늘의 작업 → 발행 확인" 이 한 작업공간 안에서 닫히지 않는다.

**L3. 같은 기능에 URL이 두 개다.**
`/calendar`↔`/calendar-v2`, `/channels`↔`/channels-v2`, `/inbox`↔`/inbox-v2`,
`/settings`↔`/settings-v2` 가 **모두 살아 있는 라우트**다. 레일은 v2만 걸지만 북마크·지원 문의·
외부 링크·앱 내부 링크는 v1로 들어간다. 사용자는 자기가 어느 화면에 있는지 모른다.

**L4. 레거시 화면이 시각적으로만 편입돼 있다.**
`RedesignLayout.vue:95-139` 가 `!important` 로 레거시 Tailwind 회색 계열을 리디자인 토큰에
매핑한다. 동작하지만, 이는 IA 통합이 아니라 겉면 덧칠이라는 신호다. 제목도 전용 i18n 키가
없으면 `route.meta.breadcrumb` 로 폴백한다(`RedesignLayout.vue:71-75`).

**L5. 착지 화면이 한국어로 고정돼 있다.**
`TodayView` 의 `대기` / `실패 N건` / `목표 N` / `N편 남음` / `목표 달성` 과
`toLocaleDateString('ko-KR')` · `NumberFormat('ko-KR')` 이 하드코딩이다. 영어 사용자가 로그인
직후 보는 첫 화면이 한국어로 섞여 나온다. `RedesignTopBar.vue:5` 의 `aria-label="ongo 홈"` 도 같다.

## (2) 메뉴 분절 / 중복

- **v1/v2 중복 4쌍** (위 L3).
- **성과가 5개로 쪼개져 있다**: `/performance`, `/analytics/compare`, `/revenue`, `/ab-tests`, `/goals`.
  사용자 머릿속에는 "성과 보기" 하나인데 메뉴에서 다섯 번 고르게 한다.
- **소통이 3개**: `/inbox-v2`, `/audience`, `/notifications`.
- **콘텐츠 자산이 6개**: `/videos`, `/templates`, `/brandkit`, `/assets`, `/subtitle-editor`, `/recycling`.
- **UGC가 최상위 그룹**을 차지한다. 크리에이터 개인 사용자에게는 통째로 무관한 축인데
  성과·소통과 같은 레벨에 놓여 레일 길이를 늘린다(capability 로 꺼지면 사라지긴 한다).
- 내비 표면 3개의 큐레이션 불일치(위 요약).

## (3) P0 / P1 — 판매 가능한 제품 관점

### P0 (이대로면 팔기 어렵다)

- **P0-1 레일 큐레이션**: 핵심 흐름 4~5개를 고정하고 나머지는 접는다. 기능은 지우지 않는다.
  *(Codex 진행 중)*
- **P0-2 v1/v2 정리**: 한쪽을 정본으로 정하고 다른 쪽은 `redirect` 로 흡수한다. 라우터만
  건드리면 되며 화면 코드는 그대로다. 두 URL이 공존하는 한 지원·분석·북마크가 전부 갈라진다.
- **P0-3 핵심 흐름을 셸 안에 가둔다**: `TodayView → /videos/{id}` 를 v2 대응 화면으로 보내거나
  상세를 셸 안에서 연다. 첫 클릭에서 나가면 "한 작업공간" 주장이 성립하지 않는다.
- **P0-4 하단 바 fail-open** — **이번에 구현함**(아래 참조).

### P1

- **P1-1 성과 5개 → 1화면 탭**: `/performance` 안에 비교·수익·A/B·목표를 탭으로. 라우트는
  유지하고 진입점만 하나로 줄인다.
- **P1-2 소통 3개 → 인박스 필터**: `/audience`, `/notifications` 를 인박스의 필터/탭으로.
- **P1-3 착지 화면 i18n**: `TodayView`/`RedesignTopBar` 하드코딩 문자열 추출 + 로케일 연동 포맷.
- **P1-4 CSS 어댑터 축소**: 화면이 실제로 마이그레이션될 때마다 `RedesignLayout` 의 `!important`
  블록을 줄여 나간다. 남아 있는 양이 곧 미완 마이그레이션의 잔량이다.

## (4) 구현 주체 구분

### Codex (현재 점유 중 — 건드리지 말 것)
- `RedesignRail.vue` (+91/-10 진행 중), `views/redesign/TodayView.vue`
- publishing / `platformPostId` 계열 전반

### Claude 가 안전하게 할 수 있는 영역
- **내비 표면 간 capability 게이팅 일치** ← 이번에 완료
- **P0-2 v1/v2 리다이렉트**: `router/index.ts` 단독 변경. 화면 코드 무관.
- **P1-3 i18n 추출**: 단, `TodayView` 는 Codex가 놓은 뒤에.
- 내비 계약 테스트 보강 (`MobileMenuSheet`, `SideNav` 는 현재 테스트가 없다).

### 조율 필요
- **P0-3 과 P1-1/P1-2** 는 라우터·레일·화면 내부 링크를 함께 건드린다. 한 명이 소유해야 하고
  Codex의 레일 작업이 착지한 뒤 순차로 진행하는 편이 안전하다.

## 이번에 구현한 것 — P0-4 하단 바 fail-open 차단

**문제**: `useNavigation` 은 서버가 활성 기능 목록을 주지 못하면 **fail-closed** 한다.
코드 주석이 이유를 명시한다 — "비활성/WIP 기능을 장애 중에 잘못 열 수 있다"
(`useNavigation.ts:85-87`). 레일과 모바일 시트는 이 규칙을 따른다.

그런데 `MobileBottomNav` 는 정적 4개 목록을 그대로 렌더했다. **모바일에서 사실상 유일한 상시
내비게이션이 규칙에서 빠져 있었다.** capability 동기화가 실패했거나 서버가 특정 기능을 끈
상태에서도 하단 바로 그 화면에 들어갈 수 있었다.

**수정**: 판정 규칙을 다시 쓰지 않고, 이미 필터링된 `allNavItems` 에 있는지만 확인해 필터링한다.
로직이 갈라지지 않게 하기 위해서다. 순서·아이콘·가운데 작성 버튼 등 디자인 의도는 그대로다.
"더보기" 버튼은 남긴다 — 목적지가 아니라 시트를 여는 버튼이고 시트 내부도 같은 규칙을 탄다.

`aria-label="모바일 하단 네비게이션"` 하드코딩도 기존 키 `nav.mainNavigation` 으로 교체했다.
**로케일 JSON 은 건드리지 않았다** — 다른 에이전트가 동시 편집 중이라 충돌면을 0으로 두려고
기존 키를 재사용했다.

### 변경 파일
- `frontend/src/components/layout/MobileBottomNav.vue`
- `frontend/src/components/layout/MobileBottomNav.test.ts` (신규, 3건)

가짜 데이터·가짜 성공 상태를 넣지 않았다. capability 가 전부 실패하면 링크는 0개가 되고,
셸의 재시도 배너(`RedesignLayout.vue:12-25`)가 그대로 사유를 보여준다.

### 검증

```bash
npx vitest run src/components/layout/MobileBottomNav.test.ts     # 3 passed
npx vitest run src/composables/useNavigation.test.ts \
                src/components/layout/MobileBottomNav.test.ts    # 6 passed
npx vitest run                                                   # 47 files, 207 tests passed
npm run build                                                    # exit 0, 1512 modules (vue-tsc 포함)
```

전체 스위트를 돌린 이유는 `useNavigation` 의 공유 상태를 새로 소비했기 때문이다.
구 코드는 4개를 무조건 렌더했으므로 신규 테스트 1·2번은 수정 전이라면 반드시 실패한다.

## 작업 중 발생한 충돌

처음 고른 개선안이 **L1(레일 서브그룹 접기 복원)** 이었는데, 편집 직전 Codex가 같은 파일에서
동일 목적의 작업(+91/-10, "핵심 작업 고정 + 나머지 접기")을 시작한 것을 확인하고 즉시 전환했다.
`TodayView.vue` 도 같은 시점에 Codex가 수정 중이라 P1-3(i18n)도 보류했다.
그래서 겹치지 않는 P0-4 를 골랐다.
