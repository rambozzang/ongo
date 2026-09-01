import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { PLANS } from './subscription'

/**
 * 화면에 적힌 가격·한도가 **서버 결제 기준과 갈라지지 않는지** 고정한다.
 *
 * ## 왜 필요한가
 *
 * 결제 금액은 서버가 정한다. 프론트는 `planType` / 패키지 이름만 보내고, 서버는
 * `PlanType`·`CreditPackage` enum 에서 금액을 계산한다. 그래서 화면이 같은 숫자를 한 벌 더
 * 들고 있으면, 서버에서 가격을 바꾼 날 **사용자가 본 금액과 청구액이 갈린다.** 그 차이는
 * 결제창이 뜨기 전까지 어디에도 드러나지 않는다.
 *
 * 그래서 실제 수익화 화면(SubscriptionView / 온보딩 / 크레딧 구매)은 인증된
 * `GET /api/v1/subscriptions/plans`, `GET /api/v1/credits/packages` 응답만 쓴다.
 *
 * ## 남은 상수 하나
 *
 * [PLANS] 만 예외다. 로그인 전 랜딩(`LoginView`)은 인증 API 를 부를 수 없어 다른 출처가
 * 없다. 없앨 수 없다면 **갈라지는 순간 실패하게** 만든다 — 이 파일이 백엔드 enum 을 직접
 * 읽어 비교한다.
 */

const HERE = resolve(fileURLToPath(import.meta.url), '..')
const FRONTEND_SRC = resolve(HERE, '..')
const PLAN_TYPE_KT = resolve(
  HERE,
  '../../../backend/onGo-common/src/main/kotlin/com/ongo/common/enums/PlanType.kt',
)

/** 서버가 무제한을 `Int.MAX_VALUE` 로 쓰는 자리를 화면은 `-1` 로 쓴다. */
const UNLIMITED = -1

interface ServerPlan {
  price: number
  yearlyPrice: number
  maxPlatforms: number
  monthlyUploads: number
  scheduleDays: number
  analyticsDays: number
  storageGB: number
  freeCredits: number
  maxTeamMembers: number
}

/**
 * `PlanType.kt` 의 enum 선언을 읽는다.
 *
 * 백엔드를 빌드하지 않고 소스를 직접 읽는 이유는, 이 테스트가 지켜야 하는 것이 **선언
 * 그 자체**이기 때문이다. 빌드 산출물이나 API 응답을 흉내 낸 픽스처를 비교하면 정작
 * 사람이 고치는 파일과는 어긋날 수 있다.
 */
function readServerPlans(): Record<string, ServerPlan> {
  const source = readFileSync(PLAN_TYPE_KT, 'utf-8')
  const declarations = source.matchAll(
    /^\s{4}(FREE|STARTER|PRO|BUSINESS)\("[^"]*",\s*([^)]+)\),$/gm,
  )

  const parsed: Record<string, ServerPlan> = {}
  for (const match of declarations) {
    const numbers = match[2]
      .split(',')
      .map((raw) => raw.trim())
      // Kotlin 은 자릿수 구분에 밑줄을 쓴다: 9_900
      .map((raw) => (raw === 'Int.MAX_VALUE' ? UNLIMITED : Number(raw.replace(/_/g, ''))))

    const [
      price, yearlyPrice, maxPlatforms, monthlyUploads,
      scheduleDays, analyticsDays, storageGB, freeCredits, maxTeamMembers,
    ] = numbers

    parsed[match[1]] = {
      price, yearlyPrice, maxPlatforms, monthlyUploads,
      scheduleDays, analyticsDays, storageGB, freeCredits, maxTeamMembers,
    }
  }
  return parsed
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) return sourceFiles(full)
    return /\.(ts|vue)$/.test(full) ? [full] : []
  })
}

describe('플랜 상수와 서버 결제 기준', () => {
  const serverPlans = readServerPlans()

  /** 파싱이 실패했는데 비교가 공허하게 통과하는 것을 막는다. */
  it('백엔드 PlanType 선언 네 개를 모두 읽는다', () => {
    expect(Object.keys(serverPlans).sort()).toEqual(['BUSINESS', 'FREE', 'PRO', 'STARTER'])
  })

  /** **핵심.** 여기가 어긋나면 로그인 전 랜딩이 청구액과 다른 가격을 광고한다. */
  it.each(['FREE', 'STARTER', 'PRO', 'BUSINESS'])('%s 가격이 서버 선언과 같다', (type) => {
    const shown = PLANS.find((p) => p.type === type)!
    const server = serverPlans[type]

    expect(shown.price).toBe(server.price)
    expect(shown.yearlyPrice).toBe(server.yearlyPrice)
  })

  /** 한도도 같이 본다 — 가격만 맞고 "무제한"이 틀리면 그것도 잘못된 광고다. */
  it.each(['FREE', 'STARTER', 'PRO', 'BUSINESS'])('%s 한도가 서버 선언과 같다', (type) => {
    const shown = PLANS.find((p) => p.type === type)!
    const server = serverPlans[type]

    expect(shown.maxPlatforms).toBe(server.maxPlatforms)
    expect(shown.maxUploadsPerMonth).toBe(server.monthlyUploads)
    expect(shown.maxScheduleDays).toBe(server.scheduleDays)
    expect(shown.analyticsPeriodDays).toBe(server.analyticsDays)
    // 서버는 GB, 화면은 MB 다. 단위가 다르면 저장 용량 표시가 1024 배 어긋난다.
    expect(shown.storageMb).toBe(server.storageGB * 1024)
    expect(shown.freeAiCredits).toBe(server.freeCredits)
    expect(shown.teamMembers).toBe(server.maxTeamMembers)
  })
})

describe('상수 사용 범위', () => {
  /**
   * **핵심 회귀 방지.** 로그인 후 화면이 다시 상수를 쓰기 시작하면, 서버 응답을 쓰는
   * 화면과 상수를 쓰는 화면이 같은 앱 안에서 다른 가격을 말하게 된다.
   */
  it('PLANS 는 로그인 전 랜딩에서만 쓴다', () => {
    // 주석에 이름이 나오는 것은 상관없다. **import 로 끌어다 쓰는 곳**만 센다.
    const importsPlans = /import\s[^;]*\bPLANS\b[^;]*from\s*['"][^'"]*types\/subscription['"]/
    const importers = sourceFiles(FRONTEND_SRC)
      .filter((file) => importsPlans.test(readFileSync(file, 'utf-8')))
      .map((file) => file.slice(FRONTEND_SRC.length + 1))

    expect(importers.sort()).toEqual(['views/LoginView.test.ts', 'views/LoginView.vue'])
  })

  /**
   * 크레딧 쪽에는 상수가 아예 없어야 한다. 로그인 전에 가격을 보여 주는 화면이 없어
   * 남겨 둘 이유도 없다.
   */
  it('크레딧 패키지 가격 상수는 남아 있지 않다', () => {
    const withConstant = sourceFiles(FRONTEND_SRC)
      .filter((file) => /CREDIT_PACKAGES\s*[:=]/.test(readFileSync(file, 'utf-8')))
      .map((file) => file.slice(FRONTEND_SRC.length + 1))

    expect(withConstant).toEqual([])
  })
})
