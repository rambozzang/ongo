import { defineStore } from 'pinia'
import type { ABTest, TestStatus, TestType } from '@/types/abtest'
import { abtestApi } from '@/api/abtest'

interface ABTestState {
  tests: ABTest[]
  activeFilter: TestStatus | 'all'
}

export const useABTestStore = defineStore('abtest', {
  state: (): ABTestState => {
    const stored = localStorage.getItem('ongo_abtests')
    const tests = stored ? JSON.parse(stored) : []

    return {
      tests,
      activeFilter: 'all'
    }
  },

  getters: {
    activeTests(): ABTest[] {
      return this.tests.filter(test => test.status === 'running')
    },

    completedTests(): ABTest[] {
      return this.tests.filter(test => test.status === 'completed')
    },

    draftTests(): ABTest[] {
      return this.tests.filter(test => test.status === 'draft')
    },

    testsByType() {
      return (type: TestType) => this.tests.filter(test => test.type === type)
    },

    filteredTests(): ABTest[] {
      if (this.activeFilter === 'all') {
        return this.tests
      }
      return this.tests.filter(test => test.status === this.activeFilter)
    },

    averageCTRImprovement(): number {
      const completed = this.completedTests
      if (completed.length === 0) return 0

      const improvements = completed.map(test => {
        const winner = test.variants.find(v => v.isWinner)
        const loser = test.variants.find(v => !v.isWinner)
        if (!winner || !loser || loser.ctr === 0) return 0
        return ((winner.ctr - loser.ctr) / loser.ctr) * 100
      })

      return improvements.reduce((a, b) => a + b, 0) / improvements.length
    }
  },

  actions: {
    async fetchTests() {
      try {
        const result = await abtestApi.list()
        if (result.tests.length > 0) {
          // Map API response to local ABTest type with fallback fields
          this.tests = result.tests.map((t) => ({
            id: t.id,
            name: t.testName,
            videoTitle: '',
            videoId: t.videoId?.toString() ?? '',
            type: 'thumbnail' as TestType,
            status: t.status.toLowerCase() as TestStatus,
            startDate: t.startedAt ?? new Date().toISOString(),
            endDate: t.endedAt ?? null,
            duration: 24,
            sampleSize: 1000,
            confidence: 95,
            createdAt: t.createdAt ?? new Date().toISOString(),
            completedAt: t.endedAt ?? null,
            variants: t.variants.map(v => ({
              id: v.id.toString(),
              label: v.variantName,
              content: v.title ?? v.description ?? '',
              thumbnailUrl: v.thumbnailUrl ?? undefined,
              impressions: v.views,
              clicks: v.clicks,
              ctr: v.views > 0 ? (v.clicks / v.views) * 100 : 0,
              watchTime: 0,
              isWinner: t.winnerVariantId === v.id,
            })),
          }))
          this.persist()
        }
      } catch {
        // Use local data
      }
    },

    async createTest(test: Omit<ABTest, 'id' | 'createdAt' | 'completedAt'>) {
      try {
        const result = await abtestApi.create({
          testName: test.name,
          videoId: test.videoId ? parseInt(test.videoId) : undefined,
          variants: test.variants.map(v => ({
            variantName: v.label,
            title: v.content,
          })),
        })
        // Add to local list after API success
        const newTest: ABTest = {
          ...test,
          id: result.id,
          createdAt: result.createdAt ?? new Date().toISOString(),
          completedAt: null,
        }
        this.tests.unshift(newTest)
      } catch {
        // Fallback to local creation
        const newTest: ABTest = {
          ...test,
          id: Math.max(0, ...this.tests.map(t => t.id)) + 1,
          createdAt: new Date().toISOString(),
          completedAt: null
        }
        this.tests.unshift(newTest)
      }
      this.persist()
    },

    async startTest(id: number) {
      try {
        await abtestApi.start(id)
      } catch {
        // Continue with local update
      }
      const test = this.tests.find(t => t.id === id)
      if (test) {
        test.status = 'running'
        test.startDate = new Date().toISOString()
        this.persist()
      }
    },

    async stopTest(id: number) {
      try {
        await abtestApi.stop(id)
      } catch {
        // Continue with local update
      }
      const test = this.tests.find(t => t.id === id)
      if (test) {
        test.status = 'cancelled'
        test.endDate = new Date().toISOString()
        this.persist()
      }
    },

    completeTest(id: number) {
      const test = this.tests.find(t => t.id === id)
      if (test) {
        test.status = 'completed'
        test.endDate = new Date().toISOString()
        test.completedAt = new Date().toISOString()
        this.persist()
      }
    },

    async deleteTest(id: number) {
      try {
        await abtestApi.remove(id)
      } catch {
        // Continue with local removal
      }
      const index = this.tests.findIndex(t => t.id === id)
      if (index !== -1) {
        this.tests.splice(index, 1)
        this.persist()
      }
    },

    declareWinner(testId: number, variantId: string) {
      const test = this.tests.find(t => t.id === testId)
      if (test) {
        test.variants.forEach(v => {
          v.isWinner = v.id === variantId
        })
        test.status = 'completed'
        test.endDate = new Date().toISOString()
        test.completedAt = new Date().toISOString()
        this.persist()
      }
    },

    setFilter(filter: TestStatus | 'all') {
      this.activeFilter = filter
    },

    persist() {
      localStorage.setItem('ongo_abtests', JSON.stringify(this.tests))
    }
  }
})

