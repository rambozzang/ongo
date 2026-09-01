/**
 * KPI 숫자 칸에 **무엇을 보여줄지** 정한다.
 *
 * ## 왜 필요한가
 *
 * 예전에는 `currentAnalytics?.views ?? 0` 이었다. 그래서 서로 다른 네 상황이 전부
 * 화면에 **"0"** 으로 나왔다.
 *
 * 1. 아직 불러오는 중
 * 2. API 오류로 못 불러옴
 * 3. 그 플랫폼의 집계 행이 없음
 * 4. **실제로 0회**
 *
 * 크리에이터는 1~3 을 "조회수가 0" 으로 읽는다. 특히 오류일 때는 오류 배너와 "0" 이
 * 동시에 떠서 어느 쪽을 믿어야 할지 알 수 없었다.
 *
 * ## 숫자 포맷 함수를 건드리지 않는 이유
 *
 * `formatCompactNumber` 는 숫자를 사람이 읽는 문자열로 바꾸는 일만 한다. 거기에
 * "미측정" 같은 상태 문구를 끼워 넣으면 숫자 포맷과 상태 판정이 한 함수에 섞여,
 * 다른 화면에서 같은 포맷을 쓸 때 예상치 못한 문자열이 나온다. 상태 판정은 여기서
 * 하고, 문구는 호출부가 i18n 으로 붙인다.
 */
export type MetricDisplay =
  | { kind: 'value'; value: number }
  | { kind: 'loading' }
  | { kind: 'unavailable'; reason: 'error' | 'noData' }

export interface MetricContext {
  /** 조회가 진행 중인가. */
  loading: boolean
  /** 마지막 조회가 실패했는가. */
  hasError: boolean
  /**
   * 그 플랫폼에 집계 행이 하나라도 있었는가.
   *
   * **합계가 0 인 것만으로는 판단할 수 없다.** 서버(`AnalyticsUseCase`)는 업로드마다
   * 행을 만들고 수집이 없으면 `dailyData = []` 에 합계 0 을 넣는다. 그래서 "실제 0회"와
   * "아직 아무것도 수집되지 않음"이 응답에서 똑같이 0 으로 보인다.
   *
   * `undefined` 는 판단 불가(필드가 없는 옛 응답)를 뜻한다.
   */
  hasData?: boolean
}

/**
 * 판정 순서에 의미가 있다.
 *
 * 1. **집계가 있고 값이 숫자면 값이 이긴다.** 서버가 준 0 은 실제 0 이므로 그대로
 *    보여준다. 재시도가 실패해도 마지막으로 성공한 값이 남아 있으면 계속 보여준다 —
 *    이미 확인한 숫자를 오류 하나로 지우면 사용자는 성과가 사라진 줄 안다. 그 숫자가
 *    최신이 아닐 수 있다는 사실은 오류 배너가 따로 말한다.
 * 2. 값이 확정되지 않았는데 조회 중이면 **로딩**이다. 아직 0 이라고 말할 근거가 없다.
 * 3. **집계가 없다고 확인된 경우**(`hasData === false`)는 미측정이다. 오류보다 먼저
 *    본다 — 재조회가 실패했더라도 "그 플랫폼에 수집된 것이 없다"는 사실은 이미 알고
 *    있고, 그게 더 정확한 안내다.
 * 4. 그 밖에 오류가 있으면 오류, 아니면 미측정이다.
 */
/**
 * 오류 배너와 함께 "이 숫자는 최신이 아닐 수 있다"를 알려야 하는가.
 *
 * 값을 보존하는 정책의 짝이다. 숫자는 남기되 신선도는 속이지 않는다 — 이 안내가 없으면
 * 오류 배너 옆의 수치가 방금 조회된 값처럼 보인다.
 *
 * 성공한 뒤에도 누를 수 있는 새로고침 버튼이 있어야 이 상태에 도달한다. 오류 배너 안의
 * 재시도 버튼은 오류일 때만 보이므로 그것만으로는 "성공 → 재조회 실패" 가 성립하지 않는다.
 */
export function shouldWarnStaleMetrics(context: {
  hasError: boolean
  hasLoadedValue: boolean
}): boolean {
  return context.hasError && context.hasLoadedValue
}

export function resolveMetricDisplay(
  value: number | null | undefined,
  context: MetricContext,
): MetricDisplay {
  const isNumber = typeof value === 'number' && Number.isFinite(value)
  if (isNumber && context.hasData !== false) {
    return { kind: 'value', value: value as number }
  }
  if (!isNumber && context.loading) return { kind: 'loading' }
  if (context.hasData === false) return { kind: 'unavailable', reason: 'noData' }
  if (context.loading) return { kind: 'loading' }
  return { kind: 'unavailable', reason: context.hasError ? 'error' : 'noData' }
}
