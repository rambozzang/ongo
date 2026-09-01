import { ref } from 'vue'
import { revenueApi } from '@/api/revenue'

/**
 * 플랫폼 광고 수익을 지금 실제로 수집하고 있는지. **서버가 정한다.**
 *
 * 서버가 실제로 측정된 광고 수익이 있다고 확인한 경우에만 수익 기반 유료 AI 기능을 연다.
 * 측정 상태가 없거나 권한이 부족한 경우 서버가 `REVENUE_DATA_UNAVAILABLE` 로 거절하며,
 * 클라이언트도 성공할 수 있는 것처럼 도구를 열어 원인 모를 오류를 보여주지 않는다.
 *
 * 어떤 플랫폼이 수익을 수집하는지는 배포된 서버의 계약이고 화면은 그 값을 볼 수 없다.
 * 클라이언트 상수로 판단하지 않고, 수익 요약 응답이 내려주는 신호만 믿는다. 나중에
 * 어댑터가 수익을 수집하기 시작하면 이 화면은 고칠 필요 없이 따라 열린다.
 *
 * 조회 실패는 **사용 불가로 본다.** 어차피 서버가 거절할 호출을 열어두는 것보다 잠시
 * 막는 편이 낫다.
 */
export function useRevenueDataAvailability() {
  const revenueDataAvailable = ref(false)
  const revenueDataUnavailableReason = ref<string | null>(null)
  const revenueDataChecked = ref(false)

  async function loadRevenueDataAvailability() {
    try {
      const summary = await revenueApi.summary('30d')
      // 서버가 이 필드를 안 내려주면 판단 근거가 없다. 열지 않는다.
      revenueDataAvailable.value = summary?.platformRevenueAvailable === true
      revenueDataUnavailableReason.value = summary?.platformRevenueUnavailableReason ?? null
    } catch {
      revenueDataAvailable.value = false
      revenueDataUnavailableReason.value = null
    } finally {
      revenueDataChecked.value = true
    }
  }

  return {
    revenueDataAvailable,
    revenueDataUnavailableReason,
    revenueDataChecked,
    loadRevenueDataAvailability,
  }
}
