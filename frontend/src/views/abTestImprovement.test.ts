import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiClient from '@/api/client'
import { useAbTestStore } from '@/stores/abtest'
import { abTestApi } from '@/api/abtest'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

/**
 * A/B 요약의 **평균 CTR 개선율이 측정 없이 숫자를 만들지 않는지** 고정한다.
 *
 * 화면(`AbTestView.vue`)은 이 값을 초록색으로 `+{{ value.toFixed(1) }}%` 로 그린다.
 * 예전에는 서버가 측정 데이터 없는 실험을 0% 로 세어 `0.0` 을 내려줬고, 화면은 `?? 0` 으로
 * 한 번 더 받쳐 **"평균 CTR 개선율 +0.0%"** 라는 성과 지표를 만들었다. 아무것도 재지
 * 않았는데 개선율이 생긴 것이다.
 *
 * 서버는 이제 측정된 실험이 없으면 `null` 을 준다. 프론트 매퍼가 그것을 숫자로 되돌리면
 * 서버 수정이 통째로 무의미해진다.
 */
describe('A/B 평균 개선율 측정 불가 계약', () => {
  const get = vi.spyOn(apiClient, 'get')

  function givenSummary(averageImprovement: number | null) {
    get.mockResolvedValue({
      data: {
        success: true,
        data: { totalTests: 3, activeTests: 1, completedTests: 2, averageImprovement },
      },
    } as never)
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  /** **매퍼가 `null` 을 0 으로 바꾸면 화면은 되돌릴 방법이 없다.** */
  it('서버가 null을 주면 0으로 바꾸지 않는다', async () => {
    givenSummary(null)

    const summary = await abTestApi.getSummary()

    expect(summary.avgCtrImprovement).toBeNull()
    // 다른 집계까지 죽이면 안 된다.
    expect(summary.completedTests).toBe(2)
  })

  it('측정된 값은 그대로 전달한다', async () => {
    givenSummary(12.5)

    expect((await abTestApi.getSummary()).avgCtrImprovement).toBe(12.5)
  })

  /** 실제로 측정된 0% 는 관측 결과다. 감추면 안 된다. */
  it('측정된 0은 null로 바꾸지 않는다', async () => {
    givenSummary(0)

    expect((await abTestApi.getSummary()).avgCtrImprovement).toBe(0)
  })

  it('요약은 서버 엔드포인트에서 읽는다', async () => {
    givenSummary(null)

    await abTestApi.getSummary()

    expect(get).toHaveBeenCalledWith('/ab-tests/summary')
  })

  // ── 우승 적용 거절이 조용히 넘어가지 않는다 ────────────────────────────────
  //
  // 서버는 노출이 측정된 변형이 2개 미만이면 `AB_TEST_NO_MEASUREMENT` 로 거절한다.
  // 스토어가 그 실패를 error 에만 적고 끝내면 화면은 그 값을 그리지 않으므로
  // **버튼이 아무 반응 없이** 끝난다 — 사용자는 적용된 줄 알거나 계속 누른다.

  it('우승 적용이 거절되면 false 를 돌려주고 사유를 남긴다', async () => {
    const post = vi.spyOn(apiClient, 'post')
      .mockRejectedValue(new Error('노출이 측정된 변형이 1개뿐이라 우승을 정할 수 없습니다.'))
    setActivePinia(createPinia())
    const store = useAbTestStore()

    const applied = await store.applyWinner(42)

    expect(applied).toBe(false)
    expect(store.error).toContain('측정된 변형')
    post.mockRestore()
  })

  /** 거절됐는데 로컬 상태에 우승을 만들어 넣으면 배지가 남는다. */
  it('거절되면 로컬에 우승 상태를 만들지 않는다', async () => {
    const post = vi.spyOn(apiClient, 'post').mockRejectedValue(new Error('거절'))
    setActivePinia(createPinia())
    const store = useAbTestStore()

    await store.applyWinner(42)

    // 배지를 그리는 것은 variants[].isWinner 다. 거절됐는데 그것이 켜지면 안 된다.
    expect(store.tests.every(t => t.variants.every(v => !v.isWinner))).toBe(true)
    post.mockRestore()
  })

  it('성공하면 true 를 돌려준다', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { success: true, data: {} } } as never)
    setActivePinia(createPinia())
    const store = useAbTestStore()

    expect(await store.applyWinner(42)).toBe(true)
    post.mockRestore()
  })

  it('ko/en 두 로케일에 측정 불가 문구가 있다', () => {
    expect(koMessages.abTest.improvementUnavailable).toBeTruthy()
    expect(koMessages.abTest.applyWinnerFailed).toBeTruthy()
    expect(koMessages.abTest.applyWinnerDone).toBeTruthy()
    expect((enMessages as { abTest: { improvementUnavailable?: string } }).abTest.improvementUnavailable)
      .toBeTruthy()
  })
})
