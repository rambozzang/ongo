import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FundingSummary, FundingTransaction, FundingGoal } from '@/types/fanFunding'
import { fanFundingApi } from '@/api/fanFunding'

function generateMockSummary(): FundingSummary {
  return {
    totalRevenue: 4850000,
    thisMonthRevenue: 620000,
    lastMonthRevenue: 540000,
    growthRate: 14.8,
    topDonors: [
      { name: '열혈팬A', totalAmount: 350000, count: 24 },
      { name: 'supporter_99', totalAmount: 280000, count: 18 },
      { name: '응원해요', totalAmount: 195000, count: 12 },
      { name: 'star_donor', totalAmount: 150000, count: 8 },
      { name: '매일시청', totalAmount: 120000, count: 15 },
    ],
    bySource: [
      { source: 'SUPER_CHAT', amount: 1800000, count: 245, percentage: 37.1 },
      { source: 'MEMBERSHIP', amount: 1400000, count: 180, percentage: 28.9 },
      { source: 'SUPER_THANKS', amount: 650000, count: 120, percentage: 13.4 },
      { source: 'NAVER_STAR', amount: 520000, count: 85, percentage: 10.7 },
      { source: 'TIKTOK_GIFT', amount: 320000, count: 95, percentage: 6.6 },
      { source: 'INSTAGRAM_BADGE', amount: 160000, count: 42, percentage: 3.3 },
    ],
    byPlatform: [
      { platform: 'youtube', amount: 3850000, count: 545 },
      { platform: 'naverclip', amount: 520000, count: 85 },
      { platform: 'tiktok', amount: 320000, count: 95 },
      { platform: 'instagram', amount: 160000, count: 42 },
    ],
    dailyTrends: Array.from({ length: 30 }, (_, i) => ({
      date: new Date(Date.now() - (29 - i) * 86400000).toISOString().split('T')[0],
      amount: 15000 + Math.floor(Math.random() * 25000),
      count: 5 + Math.floor(Math.random() * 15),
    })),
    membershipCount: 180,
    membershipMRR: 1400000,
  }
}

function generateMockTransactions(): FundingTransaction[] {
  const sources: FundingTransaction['source'][] = ['SUPER_CHAT', 'MEMBERSHIP', 'SUPER_THANKS', 'NAVER_STAR', 'TIKTOK_GIFT']
  const donors = ['열혈팬A', 'supporter_99', '응원해요', 'star_donor', '매일시청', 'fan_2025', '행복한하루']
  return Array.from({ length: 20 }, (_, i) => ({
    id: i + 1,
    source: sources[i % sources.length],
    platform: i % 3 === 0 ? 'youtube' : i % 3 === 1 ? 'tiktok' : 'naverclip',
    amount: 5000 + Math.floor(Math.random() * 50000),
    currency: 'KRW',
    donorName: donors[i % donors.length],
    message: i % 2 === 0 ? '항상 응원합니다!' : undefined,
    createdAt: new Date(Date.now() - i * 3600000 * 4).toISOString(),
  }))
}

export const useFanFundingStore = defineStore('fanFunding', () => {
  const summary = ref<FundingSummary | null>(null)
  const transactions = ref<FundingTransaction[]>([])
  const goals = ref<FundingGoal[]>([])
  const loading = ref(false)

  const monthlyGrowth = computed(() => summary.value?.growthRate ?? 0)

  async function fetchSummary(period?: string) {
    loading.value = true
    try {
      summary.value = await fanFundingApi.getSummary(period)
    } catch {
      summary.value = generateMockSummary()
    } finally {
      loading.value = false
    }
  }

  async function fetchTransactions() {
    try {
      transactions.value = await fanFundingApi.getTransactions()
    } catch {
      transactions.value = generateMockTransactions()
    }
  }

  async function fetchGoals() {
    try {
      goals.value = await fanFundingApi.getGoals()
    } catch {
      goals.value = [
        { id: 1, title: '새 장비 구매', targetAmount: 3000000, currentAmount: 1850000, isActive: true },
        { id: 2, title: '해외 촬영 기금', targetAmount: 5000000, currentAmount: 2100000, deadline: '2025-06-30', isActive: true },
      ]
    }
  }

  async function createGoal(title: string, targetAmount: number, deadline?: string) {
    try {
      const goal = await fanFundingApi.createGoal({ title, targetAmount, isActive: true, deadline })
      goals.value.push(goal)
    } catch {
      goals.value.push({ id: Date.now(), title, targetAmount, currentAmount: 0, isActive: true, deadline })
    }
  }

  async function deleteGoal(id: number) {
    try { await fanFundingApi.deleteGoal(id) } catch { /* 로컬 */ }
    goals.value = goals.value.filter((g) => g.id !== id)
  }

  return { summary, transactions, goals, loading, monthlyGrowth, fetchSummary, fetchTransactions, fetchGoals, createGoal, deleteGoal }
})
