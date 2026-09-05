import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import ko from './ko/common.json'
import en from './en/common.json'

/**
 * 온보딩 첫 화면이 광고하는 플랫폼 수가 **실제로 게시할 수 있는 수**와 갈라지지 않는지
 * 고정한다.
 *
 * ## 왜 필요한가
 *
 * 이 문구는 로그인 직후 사용자가 보는 첫 약속이다. 그런데 게시 가능 여부는 백엔드
 * `PlatformUploadCapabilities` 한 곳에서만 정해지고, 문구는 그 숫자를 손으로 베껴 둔
 * 사본이다. 사본은 조용히 낡는다 — 실제로 `Platform.TWITTER` 와 `Platform.NAVER_CLIP`
 * 이 게시 불가(`directVideoUpload`/`cloudVideoUpload` 모두 false)로 내려간 뒤에도
 * 문구는 "외 13개" 로 남아 3 + 13 = 16 개를 약속하고 있었다.
 *
 * 그 차이는 사용자가 채널을 연동하고 업로드를 눌러 실패를 볼 때까지 드러나지 않는다.
 * 그래서 여기서 백엔드 선언을 직접 읽어 비교한다 — 픽스처가 아니라 **사람이 고치는
 * 파일 그 자체**를.
 *
 * 같은 이유로 만들어진 [../types/planPricingContract.test.ts] 와 같은 방식이다.
 */

const HERE = resolve(fileURLToPath(import.meta.url), '..')
const CAPABILITY_KT = resolve(
  HERE,
  '../../../backend/onGo-application/src/main/kotlin/com/ongo/application/video/PlatformUploadCapability.kt',
)

/** 문구에 이름이 나오는 플랫폼 ↔ 백엔드 `Platform` enum. */
const PLATFORM_LABELS: Record<string, string> = {
  YouTube: 'YOUTUBE',
  TikTok: 'TIKTOK',
  Instagram: 'INSTAGRAM',
  Facebook: 'FACEBOOK',
  Threads: 'THREADS',
  Pinterest: 'PINTEREST',
  LinkedIn: 'LINKEDIN',
  WordPress: 'WORDPRESS',
  Tumblr: 'TUMBLR',
  Vimeo: 'VIMEO',
  Dailymotion: 'DAILYMOTION',
  'Naver Clip': 'NAVER_CLIP',
  'X (Twitter)': 'TWITTER',
}

/**
 * `PlatformUploadCapability.kt` 의 등재 목록을 읽어 게시 가능 여부를 뽑는다.
 *
 * 위치 인자 순서는 `PlatformUploadCapability` 의 선언을 따른다 —
 * `platform, directVideoUpload, cloudVideoUpload, scheduling, ...`.
 * 백엔드 `canPublish()` 와 같은 판정(둘 중 하나라도 true)을 쓴다.
 */
function readCapabilities(): Record<string, boolean> {
  const source = readFileSync(CAPABILITY_KT, 'utf-8')
  const declarations = source.matchAll(
    /Platform\.([A-Z_]+)\s+to\s+PlatformUploadCapability\(\s*Platform\.[A-Z_]+,\s*(true|false),\s*(true|false),/g,
  )

  const parsed: Record<string, boolean> = {}
  for (const match of declarations) {
    parsed[match[1]] = match[2] === 'true' || match[3] === 'true'
  }
  return parsed
}

/** 문구가 말하는 숫자. 두 개 이상이면 무엇을 약속하는지 읽는 사람이 알 수 없다. */
function claimedCount(description: string): number {
  const numbers = description.match(/\d+/g) ?? []
  expect(numbers, `문구에 숫자가 정확히 하나여야 한다: ${description}`).toHaveLength(1)
  return Number(numbers[0])
}

const KO = ko.onboarding.welcome.features.multiUpload.description
const EN = en.onboarding.welcome.features.multiUpload.description

describe('온보딩 플랫폼 수 문구와 백엔드 게시 가능 목록', () => {
  const capabilities = readCapabilities()
  const publishable = Object.entries(capabilities)
    .filter(([, canPublish]) => canPublish)
    .map(([platform]) => platform)

  /** 파싱이 실패했는데 비교가 공허하게 통과하는 것을 막는다. */
  it('백엔드 capability 선언을 모두 읽는다', () => {
    expect(Object.keys(capabilities).sort()).toEqual([
      'DAILYMOTION', 'FACEBOOK', 'INSTAGRAM', 'LINKEDIN', 'NAVER_CLIP', 'PINTEREST',
      'THREADS', 'TIKTOK', 'TUMBLR', 'TWITTER', 'VIMEO', 'WORDPRESS', 'YOUTUBE',
    ])
  })

  /**
   * 등재는 됐지만 게시는 못 하는 두 플랫폼. 이게 뒤집히면 광고할 수 있는 수가 늘어나므로
   * 아래 문구 단언도 같이 깨진다 — 그때 문구를 올려 주면 된다.
   */
  it('X 와 Naver Clip 은 게시 불가로 남아 있다', () => {
    expect(capabilities.TWITTER).toBe(false)
    expect(capabilities.NAVER_CLIP).toBe(false)
  })

  /** **핵심.** 여기가 어긋나면 온보딩이 만들지 않은 기능을 약속한다. */
  it.each([
    ['ko', KO],
    ['en', EN],
  ])('%s 문구가 실제 게시 가능한 수를 말한다', (_locale, description) => {
    expect(claimedCount(description)).toBe(publishable.length)
  })

  /** 두 로케일이 서로 다른 수를 말하면 어느 한쪽은 반드시 거짓말이다. */
  it('ko 와 en 이 같은 수를 말한다', () => {
    expect(claimedCount(KO)).toBe(claimedCount(EN))
  })

  /**
   * 수가 맞아도 **게시할 수 없는 플랫폼을 예시로 들면** 그것대로 잘못된 광고다.
   * 문구에 이름이 나오는 플랫폼은 전부 실제 게시 대상이어야 한다.
   */
  it.each([
    ['ko', KO],
    ['en', EN],
  ])('%s 문구가 이름을 든 플랫폼은 모두 게시 가능하다', (_locale, description) => {
    const named = Object.entries(PLATFORM_LABELS)
      .filter(([label]) => description.includes(label))
      .map(([, platform]) => platform)

    expect(named.length, `문구에서 플랫폼 이름을 하나도 못 찾았다: ${description}`).toBeGreaterThan(0)
    named.forEach((platform) => {
      expect(publishable, `게시할 수 없는 ${platform} 을(를) 예시로 들었다`).toContain(platform)
    })
  })
})
