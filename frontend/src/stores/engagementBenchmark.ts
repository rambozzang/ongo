import { defineStore } from 'pinia'
import { ref } from 'vue'
import { engagementBenchmarkApi, type EngagementBenchmarkResponse } from '@/api/engagementBenchmark'
import { useNotificationStore } from '@/stores/notification'

export const useEngagementBenchmarkStore = defineStore('engagementBenchmark', () => {
  const result = ref<EngagementBenchmarkResponse | null>(null)
  const loading = ref(false)

  async function analyze() {
    loading.value = true
    try {
      result.value = await engagementBenchmarkApi.analyze()
    } catch (e) {
      useNotificationStore().error(e instanceof Error ? e.message : '벤치마크 분석에 실패했습니다')
    } finally {
      loading.value = false
    }
  }

  function reset() {
    result.value = null
  }

  return { result, loading, analyze, reset }
})
