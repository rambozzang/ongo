import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AbTest, AbTestSummary, VideoForAbTest, AbTestType, VariantLabel } from '@/types/abtest'
import { abTestApi } from '@/api/abtest'

export const useAbTestStore = defineStore('abTest', () => {
  const tests = ref<AbTest[]>([])
  const videos = ref<VideoForAbTest[]>([])
  const summary = ref<AbTestSummary | null>(null)
  const selectedTest = ref<AbTest | null>(null)
  const activeTab = ref<'active' | 'completed' | 'create'>('active')
  const processing = ref(false)
  const error = ref<string | null>(null)

  const activeTests = computed(() => tests.value.filter(t => t.status === 'RUNNING' || t.status === 'PAUSED'))
  const completedTests = computed(() => tests.value.filter(t => t.status === 'COMPLETED'))
  const draftTests = computed(() => tests.value.filter(t => t.status === 'DRAFT'))

  async function fetchTests() {
    processing.value = true
    error.value = null
    try {
      tests.value = await abTestApi.getTests()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load tests'
    } finally {
      processing.value = false
    }
  }

  async function fetchVideos() {
    try {
      videos.value = await abTestApi.getVideos()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load videos'
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await abTestApi.getSummary()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load summary'
    }
  }

  async function createTest(videoId: number, type: AbTestType, variants: { label: VariantLabel; value: string }[], durationHours: number) {
    processing.value = true
    error.value = null
    try {
      const result = await abTestApi.createTest({ videoId, type, variants, durationHours })
      tests.value.unshift(result.test)
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create test'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function startTest(testId: number) {
    try {
      const updated = await abTestApi.startTest(testId)
      const idx = tests.value.findIndex(t => t.id === testId)
      if (idx >= 0) tests.value[idx] = updated
      if (selectedTest.value?.id === testId) selectedTest.value = updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to start test'
    }
  }

  async function pauseTest(testId: number) {
    try {
      const updated = await abTestApi.pauseTest(testId)
      const idx = tests.value.findIndex(t => t.id === testId)
      if (idx >= 0) tests.value[idx] = updated
      if (selectedTest.value?.id === testId) selectedTest.value = updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to pause test'
    }
  }

  async function resumeTest(testId: number) {
    try {
      const updated = await abTestApi.startTest(testId)
      const idx = tests.value.findIndex(t => t.id === testId)
      if (idx >= 0) tests.value[idx] = updated
      if (selectedTest.value?.id === testId) selectedTest.value = updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to resume test'
    }
  }

  async function completeTest(testId: number) {
    try {
      const updated = await abTestApi.completeTest(testId)
      const idx = tests.value.findIndex(t => t.id === testId)
      if (idx >= 0) tests.value[idx] = updated
      if (selectedTest.value?.id === testId) selectedTest.value = updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to complete test'
    }
  }

  async function deleteTest(testId: number) {
    try {
      await abTestApi.deleteTest(testId)
      tests.value = tests.value.filter(t => t.id !== testId)
      if (selectedTest.value?.id === testId) selectedTest.value = null
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to delete test'
    }
  }

  /**
   * 우승 변형을 적용한다.
   *
   * **성공 여부를 돌려준다.** 서버는 노출이 측정된 변형이 2개 미만이면
   * `AB_TEST_NO_MEASUREMENT` 로 거절한다(비교가 성립하지 않는다). 예전처럼 실패를
   * `error` 에만 적고 끝내면, 화면이 그 값을 그리지 않으므로 **버튼이 아무 반응 없이**
   * 끝난다. 사용자는 적용된 줄 알거나 계속 누른다.
   *
   * @returns 서버가 우승을 확정했으면 true. 거절했으면 false 이며 [error] 에 사유가 담긴다.
   */
  async function applyWinner(testId: number): Promise<boolean> {
    try {
      await abTestApi.applyWinner(testId)
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to apply winner'
      return false
    }
  }

  function selectTest(test: AbTest | null) {
    selectedTest.value = test
  }

  function setActiveTab(tab: 'active' | 'completed' | 'create') {
    activeTab.value = tab
  }

  return {
    tests,
    videos,
    summary,
    selectedTest,
    activeTab,
    processing,
    error,
    activeTests,
    completedTests,
    draftTests,
    fetchTests,
    fetchVideos,
    fetchSummary,
    createTest,
    startTest,
    pauseTest,
    resumeTest,
    completeTest,
    deleteTest,
    applyWinner,
    selectTest,
    setActiveTab,
  }
})
