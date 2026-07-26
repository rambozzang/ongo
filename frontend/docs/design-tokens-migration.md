# 디자인 토큰 마이그레이션 가이드

기준 커밋: `ede18eb` · 최초 작성: 2026-07-26

디자인 시스템은 잘 정의돼 있었지만 **채택률이 0.2%** 였다. 이 문서는 그 격차를 메우기 위해
추가한 토큰과, 뷰 2,900여 곳을 옮길 때 쓸 매핑표를 정리한 것이다.

## 1. 왜 아무도 안 썼는가

| 항목 | 정의 | 실사용 | 채택률 |
|---|---|---|---|
| 타이포 스케일 | 8종 | 6회 | 0.2% (raw `text-*` 2,539회) |
| 시맨틱 색상 | 4종 (`--color-success/warning/error/info`) | 거의 0 | 하드코딩 2,985회 |

원인은 **토큰이 부족해서**였다.

- 시맨틱 색상에 `base` 하나뿐이라 배지/알림에 쓸 **배경 톤과 대비되는 텍스트 톤이 없었다.**
  게다가 `--color-success`(#159b74)는 흰 배경 위 대비 **3.52:1** 로 본문 텍스트에 쓰면 AA 미달이다.
  그래서 뷰들은 `bg-green-100 text-green-800`을 쓸 수밖에 없었다.
- 타이포 스케일에는 `text-lg`(1.125rem, 155회)와 regular weight 1rem에 해당하는 단계가 **아예 없었고**,
  `caption`은 weight 500이라 regular인 `text-xs`(915회)의 대체재가 되지 못했다.

## 2. 추가된 토큰

### 2.1 시맨틱 색상 (`src/assets/tokens.css`)

각 상태마다 3단 구성이다.

| 접미사 | 용도 |
|---|---|
| (없음) | 솔리드 채움, 아이콘, 보더. **본문 텍스트로 쓰지 말 것** |
| `-subtle` | 배지 / 알림 / 콜아웃 배경 |
| `-strong` | `-subtle` 위 또는 일반 표면 위의 텍스트·아이콘 |

**라이트 모드** (base를 흰색에 16% 혼합 = subtle, 35% 어둡게 = strong)

| 토큰 | base | subtle | strong | strong/subtle 대비 | strong/white 대비 |
|---|---|---|---|---|---|
| success | `#159b74` | `#daefe9` | `#0e654b` | **5.87:1** | 7.04:1 |
| warning | `#bd7616` | `#f4e9da` | `#7b4d0e` | **6.02:1** | 7.22:1 |
| error | `#c94b58` | `#f6e2e4` | `#833139` | **6.87:1** | 8.52:1 |
| info | `#3d76c9` | `#e0e9f6` | `#284d83` | **6.92:1** | 8.47:1 |
| muted | `#707585` | `#eceef5` | `#3f4453` | **8.37:1** | 9.71:1 |

**다크 모드** (subtle은 base @ 16% alpha — 카드/elevated/tertiary 어느 표면 위에서도 합성되도록 반투명)

| 토큰 | base | subtle | strong | 최저 대비 (tertiary `#2b2f40` 위) |
|---|---|---|---|---|
| success | `#43c79b` | `rgba(67,199,155,.16)` | `#6cd3b1` | **5.43:1** |
| warning | `#e3a048` | `rgba(227,160,72,.16)` | `#e9b570` | **5.36:1** |
| error | `#ed7881` | `rgba(237,120,129,.16)` | `#f1969d` | **4.76:1** |
| info | `#79a9ef` | `rgba(121,169,239,.16)` | `#96bcf3` | **5.09:1** |
| muted | `#979cab` | `rgba(151,156,171,.16)` | `#cdd0da` | **6.67:1** |

> **검증 방법** — WCAG 2.1 상대 휘도 공식으로 4개 다크 표면(`--surface-primary #202330`,
> `--surface-elevated #282c3c`, `--surface-secondary #151722`, `--surface-tertiary #2b2f40`)
> 각각에 대해 subtle을 알파 합성한 뒤 strong과의 대비를 계산했다.
> **최저값 4.76:1 (error/tertiary)** 로 전 조합이 AA(4.5:1)를 통과한다.
> 참고로 교체 전 Tailwind 조합의 최저값은 `text-red-400` on `bg-red-900/30` = 5.24:1이었고,
> `.badge-gray`는 다크에서 `bg-gray-900/30`을 gray-900 카드 위에 올려 **배경이 사실상 보이지 않는 결함**이 있었다.

Tailwind 유틸리티로도 등록돼 있다 (`tailwind.config.js`).

```
bg-success-subtle  text-success-strong  border-success
bg-warning-subtle  text-warning-strong  border-warning
bg-error-subtle    text-error-strong    border-error
bg-info-subtle     text-info-strong     border-info
bg-muted-subtle    text-muted-strong    border-muted
```

> ⚠️ 값이 `var()` 참조라 **Tailwind opacity modifier가 동작하지 않는다.**
> `text-error-strong/50` 같은 표기는 무시되니 쓰지 말 것.

### 2.2 타이포 스케일 (`tailwind.config.js`)

기존 8단계는 유지하고 4단계를 추가했다.

| 단계 | 크기 / line-height | weight | 대체 대상 |
|---|---|---|---|
| `text-display` | 2rem / 2.5rem | 700 | `text-3xl`~`text-4xl` |
| `text-display-sm` | 1.75rem / 2.25rem | 700 | **신규** — 페이지 타이틀 (모바일) |
| `text-h1` | 1.5rem / 2rem | 700 | `text-2xl` (73회) |
| `text-h2` | 1.25rem / 1.75rem | 600 | `text-xl` (33회) |
| `text-title` | 1.125rem / 1.625rem | 600 | **신규** — `text-lg` (155회) |
| `text-h3` | 1rem / 1.5rem | 600 | `text-base` + `font-semibold` |
| `text-body-lg` | 1rem / 1.5rem | 400 | **신규** — `text-base` (35회) |
| `text-body` | 0.875rem / 1.375rem | 400 | `text-sm` (1,310회) |
| `text-body-sm` | 0.8125rem / 1.25rem | 400 | — |
| `text-body-xs` | 0.75rem / 1rem | 400 | **신규** — `text-xs` (915회) |
| `text-caption` | 0.75rem / 1rem | 500 | `text-xs font-medium` |
| `text-overline` | 0.6875rem / 1rem | 600 | 라벨 / 말머리 |

**weight는 기본값일 뿐 고정이 아니다.** Tailwind는 `fontSize` 유틸리티를 `fontWeight`보다
먼저 방출하므로(`corePluginList` 130 → 131, 빌드 산출물로 확인함)
`text-body font-semibold`는 정상적으로 600이 된다. 스케일을 쓰면서 weight를 자유롭게 덮어써도 된다.

**`display` letter-spacing 변경**: `-0.02em` → `-0.035em` 으로 조정했다.
`.page-header h1`이 쓰던 값과 통일해 2rem 단계의 중복을 없애기 위함이며, 기존 사용처는 1곳뿐이었다.

## 3. 매핑표 — 색상

### 3.1 배지 (수정 불필요)

`.badge-*` 클래스는 **이름을 그대로 둔 채 내부 구현만 토큰 기반으로 교체**했다.
아래 23곳은 **뷰를 한 줄도 안 고쳤는데 다크모드 대비가 개선됐다.**

| 클래스 | 이전 | 현재 |
|---|---|---|
| `.badge-success` | `bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400` | `--color-success-subtle` / `-strong` |
| `.badge-warning` | `bg-yellow-100 text-yellow-800 dark:…` | `--color-warning-*` |
| `.badge-danger` | `bg-red-100 text-red-800 dark:…` | `--color-error-*` |
| `.badge-gray` | `bg-gray-100 text-gray-800 dark:…` | `--color-muted-*` |
| `.badge-blue` | `bg-blue-100 text-blue-800 dark:…` | `--color-info-*` |

### 3.2 색상 → 시맨틱 매핑

| 원래 hue | 시맨틱 | 비고 |
|---|---|---|
| green, emerald, teal | **success** | |
| red, rose | **error** | |
| yellow, amber, orange | **warning** | |
| blue, sky | **info** | |
| indigo, violet, purple | **info** | indigo는 **프로젝트 금지색**. 최우선 교체 대상 (120회) |
| gray | **muted** | 상태를 뜻할 때만. 일반 텍스트는 `--text-secondary` 유지 |

### 3.3 뷰 치환 패턴 (실측 상위 빈도순)

| 이전 | 이후 |
|---|---|
| `bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400` | `badge-success` |
| `bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400` | `badge-danger` |
| `bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400` | `badge-blue` |
| `bg-yellow-100 text-yellow-800 dark:…` | `badge-warning` |
| `text-red-600 dark:text-red-400` | `text-error-strong` |
| `text-green-600 dark:text-green-400` | `text-success-strong` |
| `text-blue-600 dark:text-blue-400` | `text-info-strong` |
| `text-yellow-600 dark:text-yellow-400` | `text-warning-strong` |
| `bg-red-50 border-red-200 text-red-700` | `bg-error-subtle border-error text-error-strong` |
| `bg-blue-50 dark:bg-blue-900/20` | `bg-info-subtle` |
| `bg-green-50 dark:bg-green-900/20` | `bg-success-subtle` |
| `focus:ring-blue-500 dark:focus:ring-blue-400` | `focus:ring-primary-500` (포커스는 primary가 맞다) |
| `bg-indigo-*` / `text-indigo-*` | `bg-primary-*` / `text-primary-*` 또는 info |

**핵심 이득**: 토큰으로 옮기면 `dark:` 변형이 통째로 사라진다. 위 표에서 좌변 4개 클래스가 우변 1개가 된다.

### 3.4 교체하면 안 되는 것 (정당한 예외)

- **플랫폼 브랜드 색상** — `bg-youtube` `bg-tiktok` `bg-instagram` `bg-naver` 는 config에 등록된 이름 색상이라 가드에 걸리지 않는다. raw hex를 쓰고 있다면 이 이름으로 바꿀 것.
- **Chart.js 데이터셋 색상** — CSS 클래스가 아니라 JS 값이라 대상 밖.
- 그 외 불가피한 경우: `<!-- eslint-disable-next-line vue/no-restricted-syntax -->` 에 **사유를 함께** 남길 것.

## 4. 매핑표 — 타이포

| 이전 | 이후 | line-height 변화 |
|---|---|---|
| `text-xs` | `text-body-xs` | 1rem → 1rem (동일) |
| `text-xs font-medium` | `text-caption` | 동일 |
| `text-sm` | `text-body` | 1.25rem → **1.375rem** |
| `text-base` | `text-body-lg` | 1.5rem → 1.5rem (동일) |
| `text-base font-semibold` | `text-h3` | 동일 |
| `text-lg` | `text-title` | 1.75rem → **1.625rem** |
| `text-xl` | `text-h2` | 1.75rem → 1.75rem (동일) |
| `text-2xl` | `text-h1` | 2rem → 2rem (동일) |
| `text-3xl` | `text-display` | 2.25rem → **2.5rem** |

> `text-sm` → `text-body`, `text-lg` → `text-title`, `text-3xl` → `text-display`는
> line-height가 미세하게 달라진다. 촘촘한 테이블 행이나 고정 높이 컨테이너에서는 육안 확인이 필요하다.

## 5. 드리프트 방지 가드 (ESLint)

`eslint.config.mjs` (flat config, ESLint 9). **전부 `warn`** 이다 — 기존 위반이 많아 `error`로 두면
빌드가 즉시 깨진다. 백로그를 다 비운 뒤 `error`로 승격할 것.

### 5.1 규칙 구성

| 규칙 | 대상 | 방식 |
|---|---|---|
| `vue/no-restricted-syntax` | `<template>` | 정적 `class` (VLiteral), `:class` 안의 모든 `Literal`·`TemplateElement` |
| `no-restricted-syntax` | `<script>`, `.ts` | 문자열 리터럴 / 템플릿 리터럴 |
| `no-restricted-globals` | 스크립트 전역 | `confirm` / `alert` (`checkGlobalObject: true` 로 `window.confirm`도) |

**`vue/no-restricted-class`를 쓰지 않은 이유** — 처음엔 이 규칙을 썼는데,
내부 `extractClassNames`가 `Literal` / `TemplateLiteral` / `BinaryExpression` /
`ObjectExpression` / `ArrayExpression`만 처리하고 **`ConditionalExpression`(삼항)은 처리하지 않는다.**
이 코드베이스의 `:class`는 삼항이 지배적이라 템플릿 1,840건 중 1,430건(78%)만 잡혔다.
게다가 custom message를 지원하지 않아 "무엇으로 바꾸라"는 안내를 붙일 수 없었다.
`no-restricted-syntax` + esquery 속성 정규식으로 바꿔 **위반 파일 178/178 (100%)** 를 잡고
메시지에 이 문서 링크를 넣었다.

`confirm`/`alert`은 `no-restricted-globals`를 썼다. 전역 스코프 참조만 보므로
지역 함수 `confirm`(`ConfirmModal.vue`, `CampaignRewardsView.vue`)을 **오탐하지 않는다.**
AST 셀렉터로 `CallExpression[callee.name='confirm']`을 썼다면 이 둘이 전부 오탐이었다.

### 5.2 baseline (2026-07-26, `ede18eb` 기준)

```
$ npx eslint src --max-warnings=99999
✖ 1,315 problems (0 errors, 1,315 warnings)

    800  vue/no-restricted-syntax        <- 템플릿 raw hue
    440  no-restricted-syntax            <- 스크립트 raw hue
     37  @typescript-eslint/no-unused-vars
     29  @typescript-eslint/no-explicit-any
      6  vue/no-v-html
      2  vue/require-default-prop
      1  no-useless-escape
      0  no-restricted-globals           <- confirm/alert (아래 참고)
```

가드 도입 전 baseline은 76 warnings / 0 errors 였다. **에러는 0으로 유지된다.**

- 색상 위반 **1,240건** — 단, 이는 *문자열 단위* 카운트다.
  `class="flex bg-red-100 text-red-800"` 는 경고 1건이지만 실제 치환 대상은 2곳이다.
  **개별 유틸리티 기준 실측치는 2,985건** (템플릿 1,827 / vue script 1,155 / `.ts` 3),
  위반 파일 **178개**. 그중 **indigo 119건은 프로젝트 금지색이라 최우선**이다.
- `no-restricted-globals` **0건** — 가드를 넣을 당시엔 35건이었으나, 측정 중 다른 에이전트가
  `confirm()`/`alert()`을 `ConfirmModal`·토스트로 모두 옮겼다 (35 → 14 → 8 → 3 → 0).
  규칙이 정상 동작함은 중간 측정에서 확인했고, 이제 **재발 방지용으로 남는다.**

### 5.3 진행률 측정

```bash
# 남은 색상 위반 (문자열 단위)
npx eslint src -f json | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>console.log(JSON.parse(s).flatMap(f=>f.messages).filter(m=>String(m.ruleId).endsWith('no-restricted-syntax')).length))"

# 남은 색상 위반 (유틸리티 단위)
grep -roh "\(bg\|text\|border\|ring\)-\(indigo\|blue\|green\|red\|yellow\|purple\|pink\|orange\|emerald\|teal\|violet\)-[0-9]\{2,3\}" src --include="*.vue" | wc -l
```

## 6. 알려진 한계 / 후속 과제

1. **`.btn-danger`는 토큰으로 옮기지 않았다.** 현재 `bg-red-600 dark:bg-red-500` + 흰 텍스트다.
   `--color-error`의 다크 값(`#ed7881`)은 **다크 표면 위 텍스트용**으로 튜닝된 밝은 톤이라,
   솔리드 버튼 배경으로 쓰면 흰 글씨 대비가 ~2.8:1로 **지금(3.76:1)보다 나빠진다.**
   솔리드 채움 전용 `--color-*-solid` 쌍을 추가하는 별도 작업이 필요하다.
   (참고: 현재 `.btn-danger`의 다크 대비 3.76:1은 이미 AA 미달인 **기존 결함**이다.)
2. **`<style>` 블록 내 `@apply`는 가드가 못 잡는다.** 현재 1건뿐이라 무시 가능.
3. **`.page-stack`(0회)과 `.page-grid--feature`(0회)는 미사용 CSS다.**
   다른 에이전트가 뷰를 수정 중이라 이번엔 건드리지 않았다. raw `grid-cols-*` 123회를
   `page-grid--*`로 옮기는 작업과 함께 정리할 것.
4. **`.section-heading`도 사용처가 0이다.** 다만 `h2`/`h3`에 weight·tracking만 주고
   **font-size를 안 줘서 UA 기본 크기로 렌더되던 버그**가 있어, 스케일(`text-h2`/`text-h3`)을
   적용해 고쳐뒀다. 계속 안 쓰이면 다음 라운드에 삭제 후보.
5. 백로그 소진 후 두 `*-restricted-syntax` 규칙을 `error`로 승격할 것.
