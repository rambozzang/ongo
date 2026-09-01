import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { capabilitiesApi } from './capabilities'
import apiClient from './client'

/**
 * capability 캐시가 **오래된 답을 신선한 척 돌려주지 않는지** 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 캐시에 유효 기간이 없었다. 한 번 받아 두면 탭을 닫기 전까지 다시 묻지 않으므로, 운영자가
 * 결제 설정을 켠 뒤에도 **이미 열려 있던 탭은 계속 "결제 사용 불가"** 를 보여줬다. 사용자는
 * 설정이 켜졌다는 사실도, 새로고침이 필요하다는 사실도 알 수 없다.
 *
 * 반대 방향도 위험하다. 매 라우팅마다 물으면 요청이 붙고, 실패한 조회를 캐시에 남기면
 * 서버가 이미 막은 기능을 계속 열어 두게 된다.
 */

vi.mock('./client', () => ({
  default: { get: vi.fn() },
  unwrapResponse: (response: { data: { data: unknown } }) => response.data.data,
}))

const get = vi.mocked(apiClient.get)

function response(enabled: boolean) {
  return { data: { data: [{ key: 'payment', enabled, reason: enabled ? null : '결제 설정 없음' }] } }
}

/**
 * 다음 요청 하나를 **테스트가 원하는 시점에** 끝낼 수 있게 잡아 둔다.
 *
 * 경합은 응답 도착 순서로만 드러난다. `mockResolvedValue` 로는 순서를 만들 수 없어,
 * 해소 시점을 직접 쥔다.
 */
function deferNextRequest() {
  let settle!: (value: unknown) => void
  let reject!: (reason: unknown) => void
  get.mockReturnValueOnce(new Promise((resolve, rejectFn) => {
    settle = resolve
    reject = rejectFn
  }) as never)
  return {
    resolveWith: (enabled: boolean) => settle(response(enabled)),
    rejectWith: (error: Error) => reject(error),
  }
}

/**
 * 캐시에 값은 있지만 **유효 기간이 지난** 상태를 만든다.
 *
 * 캐시가 신선하면 일반 조회가 요청을 만들지 않으므로, "기존 캐시가 있는 상태에서 일반
 * 요청과 재검증이 함께 떠 있는" 경우는 이 상태에서만 재현된다.
 */
async function seedExpiredCache(enabled: boolean) {
  get.mockResolvedValueOnce(response(enabled) as never)
  await capabilitiesApi.list()
  vi.setSystemTime(new Date('2026-08-29T00:01:01Z'))
}

describe('capability 캐시', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    capabilitiesApi.clearCache()
    vi.useFakeTimers()
    // 캐시는 경과 시간으로 판단한다. 0 에서 시작하면 첫 호출이 이미 만료로 보일 수 있다.
    vi.setSystemTime(new Date('2026-08-29T00:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    capabilitiesApi.clearCache()
  })

  /** 라우팅 연타마다 요청이 붙으면 안 된다 — 기존 동작이다. */
  it('짧은 간격의 연속 조회는 캐시로 답한다', async () => {
    get.mockResolvedValue(response(false) as never)

    await capabilitiesApi.list()
    vi.setSystemTime(new Date('2026-08-29T00:00:30Z'))
    await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(1)
  })

  /**
   * **핵심 회귀.** 유효 기간이 지나면 다시 묻는다. 이것이 없으면 운영자가 결제를 켜도
   * 열린 탭은 영원히 옛 답을 들고 있다.
   */
  it('유효 기간이 지나면 서버에 다시 묻는다', async () => {
    get.mockResolvedValue(response(false) as never)
    await capabilitiesApi.list()

    vi.setSystemTime(new Date('2026-08-29T00:01:01Z'))
    get.mockResolvedValue(response(true) as never)
    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(true)
  })

  /** `force` 는 유효 기간과 무관하게 다시 묻는다 — 사용자가 명시적으로 확인한 경우다. */
  it('force 는 캐시가 신선해도 다시 묻는다', async () => {
    get.mockResolvedValue(response(false) as never)
    await capabilitiesApi.list()

    get.mockResolvedValue(response(true) as never)
    const items = await capabilitiesApi.list({ force: true })

    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(true)
  })

  /** 재검증 결과가 다음 조회에도 반영돼야 한다 — 아니면 화면마다 답이 다르다. */
  it('force 결과가 이후 캐시가 된다', async () => {
    get.mockResolvedValue(response(false) as never)
    await capabilitiesApi.list()
    get.mockResolvedValue(response(true) as never)
    await capabilitiesApi.list({ force: true })

    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(true)
  })

  /* ── 중복 요청·동시 클릭 ─────────────────────────────────────────── */

  /** 동시에 여러 화면이 물어도 요청은 하나다 — 기존 동작이다. */
  it('진행 중인 요청이 있으면 합류한다', async () => {
    let resolve!: (value: unknown) => void
    get.mockReturnValue(new Promise((r) => { resolve = r }) as never)

    const first = capabilitiesApi.list()
    const second = capabilitiesApi.list()
    resolve(response(true))
    await Promise.all([first, second])

    expect(get).toHaveBeenCalledTimes(1)
  })

  /** **연타 보호.** 재확인 버튼을 여러 번 눌러도 요청은 하나다. */
  it('동시 force 요청은 하나로 합친다', async () => {
    let resolve!: (value: unknown) => void
    get.mockReturnValue(new Promise((r) => { resolve = r }) as never)

    const first = capabilitiesApi.list({ force: true })
    const second = capabilitiesApi.list({ force: true })
    resolve(response(true))
    const [a, b] = await Promise.all([first, second])

    expect(get).toHaveBeenCalledTimes(1)
    expect(a).toBe(b)
  })

  /**
   * **핵심.** 이미 출발한 일반 요청은 설정이 바뀌기 **전에** 떠났을 수 있다. 거기에
   * 편승하면 재확인이 재확인이 아니게 된다.
   */
  it('force 는 진행 중인 일반 요청에 편승하지 않는다', async () => {
    let resolveFirst!: (value: unknown) => void
    get.mockReturnValueOnce(new Promise((r) => { resolveFirst = r }) as never)
    const background = capabilitiesApi.list()

    get.mockResolvedValue(response(true) as never)
    const rechecked = await capabilitiesApi.list({ force: true })

    resolveFirst(response(false))
    await background

    expect(get).toHaveBeenCalledTimes(2)
    expect(rechecked[0].enabled).toBe(true)
  })

  /* ── 일반 요청과 강제 재검증이 동시에 떠 있을 때 ─────────────────── */

  /**
   * **핵심 회귀 1.** 진행 중인 일반 요청은 설정이 바뀌기 **전에** 떠났을 수 있다.
   * 거기에 편승하면 재확인이 재확인이 아니게 된다.
   */
  it('일반 요청이 진행 중이어도 force 는 별도 요청을 보낸다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()

    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    expect(get).toHaveBeenCalledTimes(2)

    forced.resolveWith(true)
    background.resolveWith(false)
    await Promise.all([normal, rechecked])
  })

  /**
   * **핵심 회귀 2.** 예전에는 진행 중인 요청을 한 칸에만 들고 있어, 강제 요청이 일반
   * 요청의 자리를 덮어썼다. 그러면 먼저 끝난 일반 요청의 뒷정리가 **진행 중인 강제 요청의
   * 자리를 지워**, 뒤이은 재확인이 합류하지 못하고 요청을 하나 더 만들었다.
   */
  it('일반 요청이 먼저 끝나도 진행 중인 force 추적이 남는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const firstRecheck = capabilitiesApi.list({ force: true })

    // 일반 요청이 먼저 끝난다. 이때 force 의 자리를 지워서는 안 된다.
    background.resolveWith(false)
    await normal

    const secondRecheck = capabilitiesApi.list({ force: true })
    expect(get).toHaveBeenCalledTimes(2)

    forced.resolveWith(true)
    const [a, b] = await Promise.all([firstRecheck, secondRecheck])

    // 합류했으므로 같은 응답이고, 최종 캐시도 재검증 결과다.
    expect(a).toBe(b)
    expect(a[0].enabled).toBe(true)
    expect((await capabilitiesApi.list())[0].enabled).toBe(true)
    expect(get).toHaveBeenCalledTimes(2)
  })

  /**
   * **핵심 회귀 3.** 응답 순서는 보장되지 않는다. 먼저 출발한 일반 요청의 늦은 응답이
   * 재검증 결과를 덮으면, 운영자가 결제를 켠 직후 누른 재확인이 **스스로 되돌려진다** —
   * 화면에는 재확인이 실패한 것처럼 보이고 원인은 어디에도 남지 않는다.
   */
  it('force 가 먼저 끝나면 뒤늦은 일반 응답이 그 결과를 덮지 않는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    forced.resolveWith(true)
    expect((await rechecked)[0].enabled).toBe(true)

    // 설정이 바뀌기 전에 떠난 응답이 이제 도착한다.
    background.resolveWith(false)
    await normal

    expect((await capabilitiesApi.list())[0].enabled).toBe(true)
  })

  /** 늦게 도착한 응답도 **호출자에게는** 자기 답을 준다 — 캐시만 건드리지 않는다. */
  it('늦게 도착한 응답은 캐시만 건드리지 않고 호출자에게는 전달된다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    forced.resolveWith(true)
    await rechecked
    background.resolveWith(false)

    expect((await normal)[0].enabled).toBe(false)
    expect((await capabilitiesApi.list())[0].enabled).toBe(true)
  })

  /**
   * **핵심 회귀.** 캐시가 아직 신선해도 재검증이 떠 있으면 그쪽을 기다린다.
   *
   * 신선함을 먼저 보면, 누군가 재확인을 누른 **직후** 다른 화면이 물었을 때 옛 캐시가
   * 즉시 나간다. 곧 도착할 재검증 결과와 다른 값이라 같은 순간 두 화면이 서로 다른 결제
   * 상태를 본다 — 한쪽은 결제를 열고 다른 쪽은 막는다.
   */
  it('신선한 캐시가 있어도 진행 중인 force 에 합류한다', async () => {
    get.mockResolvedValueOnce(response(false) as never)
    await capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(1)

    // 캐시는 아직 신선하다. 그 상태에서 재검증이 시작된다.
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })
    const normal = capabilitiesApi.list()

    // 옛 캐시로 즉시 답하지 않고 같은 요청을 기다린다.
    expect(normal).toBe(rechecked)
    expect(get).toHaveBeenCalledTimes(2)

    forced.resolveWith(true)
    const [a, b] = await Promise.all([rechecked, normal])

    expect(a).toBe(b)
    expect(b[0].enabled).toBe(true)
  })

  /**
   * 재검증이 끝난 **뒤**에는 다시 캐시로 답한다 — 합류 규칙이 TTL 을 없애면 안 된다.
   */
  it('재검증이 끝난 뒤에는 그 결과를 캐시로 답한다', async () => {
    get.mockResolvedValueOnce(response(false) as never)
    await capabilitiesApi.list()
    get.mockResolvedValueOnce(response(true) as never)
    await capabilitiesApi.list({ force: true })

    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(true)
  })

  /**
   * 재검증이 실패하면 **합류한 일반 조회도 같이 실패한다.** 확인하지 못한 것을 확인된
   * 값으로 바꾸지 않는다.
   *
   * 실패가 아직 유효한 캐시를 버리지는 않는다 — 그 값은 60 초 안에 서버에서 실제로 받은
   * 응답이다. 대신 유효 기간이 지나면 다시 묻는다. 화면 쪽은 이와 별개로 fail-closed 다
   * (`usePaymentAvailability` 가 실패를 사용 불가로 본다).
   */
  it('합류한 force 가 실패하면 일반 조회도 같이 실패한다', async () => {
    get.mockResolvedValueOnce(response(true) as never)
    await capabilitiesApi.list()

    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })
    const normal = capabilitiesApi.list()
    forced.rejectWith(new Error('capability offline'))

    await expect(rechecked).rejects.toThrow('capability offline')
    await expect(normal).rejects.toThrow('capability offline')

    // 실패한 재검증이 유효 기간을 늘리지 않는다. 만료되면 다시 묻는다.
    vi.setSystemTime(new Date('2026-08-29T00:01:01Z'))
    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(3)
    expect(items[0].enabled).toBe(false)
  })

  /** 일반 조회는 이미 떠 있는 강제 재검증에 합류한다 — 그쪽이 더 새롭다. */
  it('강제 재검증이 진행 중이면 일반 조회는 그것에 합류한다', async () => {
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    const normal = capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(1)

    forced.resolveWith(true)
    const [a, b] = await Promise.all([rechecked, normal])
    expect(a).toBe(b)
  })

  /* ── 재검증보다 먼저 출발한 요청은 캐시를 채우지 못한다 ─────────── */

  /*
   * 강제 재검증은 "지금 캐시를 믿지 못하겠다"는 선언이다. 그때 이미 날아가 있던 응답은
   * 그 선언이 가리키는 **옛 상태**를 담고 있다. 그래서 재검증이 성공하든 실패하든,
   * 그보다 먼저 출발한 요청은 캐시도 `cachedAt` 도 건드리지 못한다.
   *
   * 호출자에게는 자기 응답을 그대로 준다 — 요청한 사람에게 답을 숨길 이유는 없다.
   *
   * 아래 검증은 모두 **"다음 조회가 새 요청을 보내는가"** 로 한다. 오래된 응답이 캐시를
   * 채웠다면 그 조회는 요청 없이 캐시로 답해 버린다.
   */

  /** 순서 ①: 일반 성공 → 재검증 성공. 최종 캐시는 재검증 결과다. */
  it('normal→force 모두 성공하면 재검증 결과만 캐시가 된다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    background.resolveWith(false)
    expect((await normal)[0].enabled).toBe(false)
    forced.resolveWith(true)
    await rechecked

    expect((await capabilitiesApi.list())[0].enabled).toBe(true)
    expect(get).toHaveBeenCalledTimes(2)
  })

  /**
   * **핵심 회귀.** 순서 ②: 재검증 실패 → 일반 성공.
   *
   * 예전에는 재검증이 실패하면 세대 비교의 기준이 오르지 않아, 뒤늦게 도착한 일반 응답이
   * 캐시를 채우고 유효 기간을 60 초 늘렸다. 확인하지 못한 상태가 **확인된 것처럼** 굳어져
   * 실패한 재확인을 우회한다.
   */
  it('force 실패 뒤 늦게 온 일반 응답이 캐시를 채우지 않는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    forced.rejectWith(new Error('capability offline'))
    await expect(rechecked).rejects.toThrow('capability offline')

    background.resolveWith(true)
    // 호출자에게는 자기 응답을 준다.
    expect((await normal)[0].enabled).toBe(true)

    // 캐시는 비어 있어야 한다 — 다음 조회가 서버에 다시 묻는다.
    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(3)
    expect(items[0].enabled).toBe(false)
  })

  /**
   * **핵심 회귀.** 순서 ③: 일반 성공 → 재검증 실패.
   *
   * 일반 응답이 먼저 도착했다고 해서 확인값이 되지는 않는다. 재검증이 실패한 이상 그 값도
   * 재검증 이전 상태다.
   */
  it('일반 응답이 먼저 와도 force 가 실패하면 확인값으로 남지 않는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    background.resolveWith(true)
    expect((await normal)[0].enabled).toBe(true)
    forced.rejectWith(new Error('capability offline'))
    await expect(rechecked).rejects.toThrow()

    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(3)
    expect(items[0].enabled).toBe(false)
  })

  /* ── 같은 순서, 기존 캐시가 있는 상태 ───────────────────────────── */

  /** 순서 ①(기존 캐시): 최종 캐시는 재검증 결과다. */
  it('기존 캐시가 있어도 normal→force 성공은 재검증 결과가 캐시가 된다', async () => {
    await seedExpiredCache(false)
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    background.resolveWith(false)
    await normal
    forced.resolveWith(true)
    await rechecked

    expect((await capabilitiesApi.list())[0].enabled).toBe(true)
    expect(get).toHaveBeenCalledTimes(3)
  })

  /**
   * 순서 ②(기존 캐시): 재검증 실패 뒤 늦게 온 일반 응답이 **옛 캐시를 덮지도, 유효 기간을
   * 늘리지도** 못한다.
   */
  it('기존 캐시가 있어도 force 실패 뒤 늦은 일반 응답이 캐시를 덮지 않는다', async () => {
    await seedExpiredCache(false)
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    forced.rejectWith(new Error('capability offline'))
    await expect(rechecked).rejects.toThrow()
    background.resolveWith(true)
    expect((await normal)[0].enabled).toBe(true)

    // 유효 기간이 늘지 않았으므로 다음 조회는 다시 묻는다.
    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(4)
    expect(items[0].enabled).toBe(false)
  })

  /** 순서 ③(기존 캐시): 일반 응답이 먼저 와도 마찬가지다. */
  it('기존 캐시가 있어도 일반 응답 선착이 확인값이 되지 않는다', async () => {
    await seedExpiredCache(false)
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    background.resolveWith(true)
    await normal
    forced.rejectWith(new Error('capability offline'))
    await expect(rechecked).rejects.toThrow()

    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(4)
    expect(items[0].enabled).toBe(false)
  })

  /** 정상 재검증은 당연히 캐시를 갱신한다 — 위 장벽이 그것까지 막으면 안 된다. */
  it('정상 force 응답은 캐시를 갱신한다', async () => {
    await seedExpiredCache(false)

    get.mockResolvedValueOnce(response(true) as never)
    await capabilitiesApi.list({ force: true })

    const items = await capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(true)
  })

  /* ── 무효화 중 진행 중인 요청 ────────────────────────────────────── */

  /**
   * **핵심 회귀.** 무효화 직후의 조회가 무효화된 요청에 합류하면, 방금 버린 것과 같은
   * 답을 받는다 — 버린 적이 없는 것과 같다. feature-unavailable 에서 '다시 시도'를 눌러도
   * 같은 판단이 되풀이된다.
   */
  it('clearCache 중 일반 요청이 있으면 다음 조회는 새 요청이다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()

    capabilitiesApi.clearCache()

    get.mockResolvedValue(response(false) as never)
    const fresh = capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(2)

    background.resolveWith(true)
    // 기존 호출자는 자기 응답을 그대로 받는다.
    expect((await normal)[0].enabled).toBe(true)
    expect((await fresh)[0].enabled).toBe(false)
  })

  /**
   * 재검증이 끝난 뒤에도, **그보다 먼저 뜬 일반 요청은 새 호출자에게 물려주지 않는다.**
   *
   * 그 응답은 캐시를 채울 자격이 없다고 이미 판정한 것이다. 새 조회가 거기 합류하면
   * 캐시에 넣지 못할 답을 확인값처럼 받게 된다 — 재확인이 실패한 직후 다른 화면이
   * 재검증 이전의 상태를 그리는 경로다.
   */
  it('force 시작 이후에는 먼저 뜬 일반 요청에 새 조회가 합류하지 않는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    forced.rejectWith(new Error('capability offline'))
    await expect(rechecked).rejects.toThrow()

    // 일반 요청은 아직 떠 있다. 새 조회는 그것에 합류하지 않고 다시 묻는다.
    get.mockResolvedValue(response(false) as never)
    const fresh = capabilitiesApi.list()
    expect(get).toHaveBeenCalledTimes(3)

    background.resolveWith(true)
    // 원래 호출자는 자기 응답을 그대로 받는다.
    expect((await normal)[0].enabled).toBe(true)
    expect((await fresh)[0].enabled).toBe(false)
  })

  /** 재검증도 마찬가지다 — 무효화 시점에 떠 있었다면 그 답은 이미 옛것이다. */
  it('clearCache 중 force 요청이 있으면 다음 재확인은 새 요청이다', async () => {
    const forced = deferNextRequest()
    const stale = capabilitiesApi.list({ force: true })

    capabilitiesApi.clearCache()

    get.mockResolvedValue(response(false) as never)
    const fresh = capabilitiesApi.list({ force: true })
    expect(get).toHaveBeenCalledTimes(2)

    forced.resolveWith(true)
    await stale
    expect((await fresh)[0].enabled).toBe(false)
    // 무효화 이전 응답은 캐시를 채우지 못한다.
    expect((await capabilitiesApi.list())[0].enabled).toBe(false)
  })

  /* ── 실패 ─────────────────────────────────────────────────────────── */

  /** 실패를 캐시에 남기면 다음 호출이 조용히 성공한 것처럼 보인다. */
  it('조회에 실패하면 예외를 그대로 올린다', async () => {
    get.mockRejectedValue(new Error('capability offline'))

    await expect(capabilitiesApi.list()).rejects.toThrow('capability offline')
  })

  /**
   * **핵심.** 실패한 재검증이 유효 기간을 늘려서는 안 된다. 늘어나면 서버가 이미 막은
   * 기능을 오래된 값으로 계속 열어 두게 된다.
   */
  it('재검증에 실패해도 오래된 값을 신선한 것으로 되돌리지 않는다', async () => {
    get.mockResolvedValue(response(true) as never)
    await capabilitiesApi.list()

    vi.setSystemTime(new Date('2026-08-29T00:01:01Z'))
    get.mockRejectedValue(new Error('capability offline'))
    await expect(capabilitiesApi.list()).rejects.toThrow()

    // 다음 호출도 캐시로 답하지 않고 다시 묻는다.
    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(3)
    expect(items[0].enabled).toBe(false)
  })

  /**
   * 실패한 재검증이 자리를 붙들고 있으면, 다음 재확인이 **끝난 요청에 합류해** 영원히
   * 다시 묻지 못한다. 버튼은 눌리는데 아무 일도 일어나지 않는 상태다.
   */
  it('force 가 실패해도 다음 재확인은 다시 묻는다', async () => {
    const forced = deferNextRequest()
    const failing = capabilitiesApi.list({ force: true })
    forced.rejectWith(new Error('capability offline'))
    await expect(failing).rejects.toThrow('capability offline')

    get.mockResolvedValue(response(true) as never)
    const retried = await capabilitiesApi.list({ force: true })

    expect(get).toHaveBeenCalledTimes(2)
    expect(retried[0].enabled).toBe(true)
  })

  /**
   * **핵심.** 재검증이 실패했는데 먼저 떠난 일반 응답이 캐시를 채우면, 확인하지 못한
   * 상태가 **확인된 것처럼** 보인다. 실패는 실패로 남아야 다음 호출이 다시 묻는다.
   */
  it('force 가 실패하면 동시에 온 일반 응답을 확인된 값으로 삼지 않는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    background.resolveWith(true)
    await normal
    forced.rejectWith(new Error('capability offline'))
    await expect(rechecked).rejects.toThrow()

    // 실패한 재검증 이후의 조회는 캐시로 답하지 않고 서버에 다시 묻는다.
    vi.setSystemTime(new Date('2026-08-29T00:01:01Z'))
    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(3)
    expect(items[0].enabled).toBe(false)
  })

  /** 일반 요청이 실패해도 진행 중인 재검증은 자기 답을 끝까지 가져와야 한다. */
  it('일반 요청이 실패해도 진행 중인 force 는 살아남는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()
    const forced = deferNextRequest()
    const rechecked = capabilitiesApi.list({ force: true })

    background.rejectWith(new Error('capability offline'))
    await expect(normal).rejects.toThrow()

    forced.resolveWith(true)

    expect((await rechecked)[0].enabled).toBe(true)
    expect((await capabilitiesApi.list())[0].enabled).toBe(true)
    expect(get).toHaveBeenCalledTimes(2)
  })

  /**
   * 무효화 시점보다 **먼저 떠난** 응답이 캐시를 다시 채우면, 무효화한 적이 없는 것과 같다.
   * feature-unavailable 에서 '다시 시도'를 눌러도 같은 판단이 되풀이된다.
   */
  it('clearCache 시점보다 먼저 떠난 응답은 캐시를 다시 채우지 않는다', async () => {
    const background = deferNextRequest()
    const normal = capabilitiesApi.list()

    capabilitiesApi.clearCache()
    background.resolveWith(true)
    await normal

    get.mockResolvedValue(response(false) as never)
    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(false)
  })

  /** feature-unavailable 복구 흐름이 쓰는 경로다. 무효화 뒤에는 반드시 다시 묻는다. */
  it('clearCache 뒤에는 즉시 다시 묻는다', async () => {
    get.mockResolvedValue(response(false) as never)
    await capabilitiesApi.list()

    capabilitiesApi.clearCache()
    get.mockResolvedValue(response(true) as never)
    const items = await capabilitiesApi.list()

    expect(get).toHaveBeenCalledTimes(2)
    expect(items[0].enabled).toBe(true)
  })
})
