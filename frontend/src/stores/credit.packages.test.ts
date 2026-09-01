import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCreditStore } from './credit'
import { creditApi } from '@/api/credit'

/**
 * 크레딧 패키지 목록이 **서버 응답 그대로** 화면까지 가는지, 그리고 **조회 실패를 빈 목록으로
 * 바꾸지 않는지** 고정한다.
 *
 * 결제 금액은 서버가 `CreditPackage` enum 에서 계산한다. 화면이 자기 숫자를 그리면 사용자가
 * 본 금액과 청구액이 갈리고, 그 차이는 결제창이 뜨기 전까지 드러나지 않는다.
 *
 * 실패를 `[]` 로 두는 것도 같은 종류의 거짓말이다. 빈 목록은 "살 수 있는 것이 없다"는
 * **다른 사실**이라, 사용자는 판매 중단으로 읽는다. 못 불러온 것은 `null` 이어야 한다.
 */

vi.mock('@/api/credit', () => ({
  creditApi: { getPackages: vi.fn(), getBalance: vi.fn(), getTransactions: vi.fn() },
}))

/** 서버 `GET /credits/packages` 응답 모양 그대로. */
const SERVER_PACKAGES = [
  { name: 'STARTER', displayName: '스타터 팩', credits: 500, price: 4900, validDays: 30, pricePerCredit: 9.8 },
  { name: 'BUSINESS', displayName: '비즈니스 팩', credits: 10000, price: 49900, validDays: 180, pricePerCredit: 5 },
]

describe('크레딧 패키지 조회', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  /** 조회 전에는 아직 아무것도 모른다 — 빈 목록(살 것이 없다)과 구별한다. */
  it('조회 전에는 목록이 null 이다', () => {
    expect(useCreditStore().packages).toBeNull()
  })

  /** **핵심.** 서버 숫자를 그대로 옮긴다. 여기서 반올림하거나 갈아끼우면 드리프트가 된다. */
  it('서버가 준 가격·수량을 그대로 옮긴다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    const store = useCreditStore()

    await store.fetchPackages()

    expect(store.packages).toEqual([
      { key: 'STARTER', name: '스타터 팩', credits: 500, price: 4900, pricePerCredit: 9.8, validDays: 30 },
      { key: 'BUSINESS', name: '비즈니스 팩', credits: 10000, price: 49900, pricePerCredit: 5, validDays: 180 },
    ])
  })

  /**
   * 결제 요청에는 **서버가 검증하는 식별자**만 간다. 표시명('스타터 팩')을 보내면 서버가
   * 패키지를 찾지 못해 결제창 자체가 열리지 않는다.
   */
  it('결제 식별자로는 표시명이 아니라 enum 이름을 남긴다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    const store = useCreditStore()

    await store.fetchPackages()

    expect(store.packages!.map((p) => p.key)).toEqual(['STARTER', 'BUSINESS'])
  })

  /** **핵심.** 실패를 빈 목록으로 바꾸면 "판매 중단"으로 읽힌다. 다른 사실이다. */
  it('조회에 실패하면 빈 목록이 아니라 null 로 남기고 사유를 알린다', async () => {
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('서버가 응답하지 않습니다'))
    const store = useCreditStore()

    await store.fetchPackages()

    expect(store.packages).toBeNull()
    expect(store.packagesError).toBe('서버가 응답하지 않습니다')
  })

  /** 한 번 받아 둔 목록이 실패 뒤에도 남아 있으면, 그것이 곧 오래된 가격 표시다. */
  it('한 번 성공한 뒤 실패하면 예전 목록을 계속 보여주지 않는다', async () => {
    const store = useCreditStore()
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    await store.fetchPackages()

    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('일시적 장애'))
    await store.fetchPackages()

    expect(store.packages).toBeNull()
  })

  /** 다시 시도해서 성공하면 사유는 사라져야 한다 — 남으면 성공 화면에 실패 문구가 붙는다. */
  it('재시도로 성공하면 실패 사유를 지운다', async () => {
    const store = useCreditStore()
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('일시적 장애'))
    await store.fetchPackages()

    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    await store.fetchPackages()

    expect(store.packagesError).toBeNull()
    expect(store.packages).toHaveLength(2)
  })

  /** 조회 실패가 예외로 새어 나가면 화면이 unhandled rejection 으로 끝난다. */
  it('조회 실패를 예외로 던지지 않는다', async () => {
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('일시적 장애'))

    await expect(useCreditStore().fetchPackages()).resolves.toBeUndefined()
  })
})
