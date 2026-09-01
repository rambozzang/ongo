import { ref } from 'vue'
import { aiApi } from '@/api/ai'

/**
 * 서버 `AiFeature` 와 화면이 같은 크레딧 단가를 보도록 하는 조회 계약.
 *
 * AI 요청은 서버가 최종 차감액을 결정한다. 화면에 단가를 복제해 두면 서버 단가가
 * 바뀐 뒤 사용자가 본 금액과 실제 차감액이 달라질 수 있으므로, 조회 전·실패 시에는
 * 비용을 알 수 없는 상태로 남기고 호출을 열지 않는다.
 */
export function useAiFeaturePricing() {
  const costs = ref<Record<string, number> | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function load() {
    loading.value = true
    costs.value = null
    error.value = null
    try {
      const features = await aiApi.getFeatures()
      const next: Record<string, number> = {}
      for (const feature of features) {
        if (!feature.key.trim() || !Number.isInteger(feature.creditCost) || feature.creditCost < 0) {
          throw new Error('AI 비용 정보가 올바르지 않습니다.')
        }
        next[feature.key] = feature.creditCost
      }
      if (Object.keys(next).length === 0) {
        throw new Error('AI 비용 정보가 비어 있습니다.')
      }
      costs.value = next
    } catch {
      // 내부 응답·설정 오류를 그대로 노출하지 않고, 가격을 모르는 상태로 닫는다.
      error.value = 'AI 기능 비용을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'
    } finally {
      loading.value = false
    }
  }

  function costOf(featureKey: string): number | null {
    return costs.value?.[featureKey] ?? null
  }

  function hasCostsFor(featureKeys: string[]): boolean {
    return costs.value !== null && featureKeys.every((key) => costOf(key) != null)
  }

  return { costs, loading, error, load, costOf, hasCostsFor }
}
