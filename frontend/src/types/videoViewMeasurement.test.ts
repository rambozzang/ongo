import { describe, expect, it } from 'vitest'
import type { Video } from './video'

/**
 * 영상 목록의 총 조회수 **계약**을 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버는 `viewsByUploadId[it.id] ?: 0L` 로 **집계 행이 없는 업로드에 0 을 더했다.**
 * YouTube 에 올렸지만 아직 동기화 전인 영상이 목록에서 "조회수 0회" 로 나갔고, 실제로
 * 0 회였던 영상과 완전히 같은 모양이었다. 서버는 이제 세 상태를 구분해 준다.
 *
 * - `totalViews = null`, `pendingViewUploads = 0` → 수집하는 플랫폼이 없다(측정 불가)
 * - `totalViews = null`, `pendingViewUploads > 0` → 아직 집계 전이다(수집 대기)
 * - `totalViews = 0` → **실측 0**
 *
 * ## 이 파일이 지키는 것
 *
 * **현재 이 필드를 읽는 화면은 없다.** 그래서 표시 회귀 테스트를 쓸 대상이 없다.
 * 대신 타입 계약을 고정한다 — 아래 주석의 타입 표기는 `vue-tsc` 가 검사하므로,
 * 누가 `totalViews` 를 `number` 로 좁히면 타입 검사에서 깨진다. 그 순간부터
 * `?? 0` 이 눈에 띄지 않게 들어올 수 있기 때문이다.
 */
describe('영상 목록 조회수 계약', () => {
  /** 미측정을 표현할 수 있어야 한다. `number` 로 좁히면 vue-tsc 가 여기서 깨진다. */
  it('미측정은 null 로 표현된다', () => {
    const unmeasured: Video['totalViews'] = null

    expect(unmeasured).toBeNull()
  })

  /** **측정된 0 은 관측이다.** null 과 같은 값이 되면 안 된다. */
  it('실측 0 과 미측정은 서로 다른 값이다', () => {
    const unmeasured: Video['totalViews'] = null
    const measuredZero: Video['totalViews'] = 0

    expect(measuredZero).toBe(0)
    expect(unmeasured).not.toBe(measuredZero)
  })

  /**
   * **이 줄이 금지하는 동작을 그대로 보여준다.** `?? 0` 을 하면 위에서 구분한 두 상태가
   * 다시 같아진다 — 서버가 고친 버그를 화면에서 되살리는 셈이다.
   */
  it('?? 0 을 하면 미측정과 실측 0 이 구분되지 않는다', () => {
    const unmeasured: Video['totalViews'] = null
    const measuredZero: Video['totalViews'] = 0

    expect(unmeasured ?? 0).toBe(measuredZero)
  })

  /** 부분 합계를 전체 합계로 오해하지 않으려면 대기 건수가 필요하다. */
  it('부분 집계는 측정값과 대기 건수를 함께 표현한다', () => {
    const partial: Pick<Video, 'totalViews' | 'pendingViewUploads'> = {
      totalViews: 1000,
      pendingViewUploads: 1,
    }

    expect(partial.totalViews).toBe(1000)
    expect(partial.pendingViewUploads).toBeGreaterThan(0)
  })
})
