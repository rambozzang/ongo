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

  async function applyWinner(testId: number) {
    try {
      await abTestApi.applyWinner(testId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to apply winner'
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
    applyWinner,
    selectTest,
    setActiveTab,
  }
})
