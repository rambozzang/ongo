import { defineStore } from 'pinia'
import { ref } from 'vue'
import { revenueSplitApi } from '@/api/revenueSplit'
import type {
  RevenueSplit,
  RevenueSplitSummary,
  CreateRevenueSplitRequest,
} from '@/types/revenueSplit'

export const useRevenueSplitStore = defineStore('revenueSplit', () => {
  const splits = ref<RevenueSplit[]>([])
  const summary = ref<RevenueSplitSummary>({
    totalSplits: 0,
    pendingAmount: 0,
    distributedAmount: 0,
    totalMembers: 0,
    averageSplit: 0,
  })
  const isLoading = ref(false)

  const mockSplits: RevenueSplit[] = [
    {
      id: 1,
      title: '2025년 3월 유튜브 수익',
      description: '3월 유튜브 광고 수익 분배',
      totalAmount: 5200000,
      currency: 'KRW',
      status: 'DISTRIBUTED',
      period: '2025-03',
      members: [
        { id: 1, name: '김크리에이터', email: 'kim@example.com', role: '크리에이터', percentage: 60, amount: 3120000, paymentStatus: 'PAID', paidAt: '2025-03-25T10:00:00Z' },
        { id: 2, name: '이편집자', email: 'lee@example.com', role: '편집자', percentage: 25, amount: 1300000, paymentStatus: 'PAID', paidAt: '2025-03-25T10:00:00Z' },
        { id: 3, name: '박매니저', email: 'park@example.com', role: '매니저', percentage: 15, amount: 780000, paymentStatus: 'PAID', paidAt: '2025-03-25T10:00:00Z' },
      ],
      createdAt: '2025-03-20T09:00:00Z',
      updatedAt: '2025-03-25T10:00:00Z',
    },
    {
      id: 2,
      title: '2025년 3월 스폰서십 수익',
      description: '삼성전자 협찬 수익 분배',
      totalAmount: 8000000,
      currency: 'KRW',
      status: 'PENDING',
      period: '2025-03',
      members: [
        { id: 4, name: '김크리에이터', email: 'kim@example.com', role: '크리에이터', percentage: 50, amount: 4000000, paymentStatus: 'PENDING' },
        { id: 5, name: '최작가', email: 'choi@example.com', role: '작가', percentage: 20, amount: 1600000, paymentStatus: 'PENDING' },
        { id: 6, name: '이편집자', email: 'lee@example.com', role: '편집자', percentage: 20, amount: 1600000, paymentStatus: 'PENDING' },
        { id: 7, name: '박매니저', email: 'park@example.com', role: '매니저', percentage: 10, amount: 800000, paymentStatus: 'PENDING' },
      ],
      createdAt: '2025-03-28T14:00:00Z',
      updatedAt: '2025-03-28T14:00:00Z',
    },
    {
      id: 3,
      title: '2025년 2월 유튜브 수익',
      description: '2월 유튜브 광고 수익 분배',
      totalAmount: 4800000,
      currency: 'KRW',
      status: 'APPROVED',
      period: '2025-02',
      members: [
        { id: 8, name: '김크리에이터', email: 'kim@example.com', role: '크리에이터', percentage: 60, amount: 2880000, paymentStatus: 'PENDING' },
        { id: 9, name: '이편집자', email: 'lee@example.com', role: '편집자', percentage: 25, amount: 1200000, paymentStatus: 'PENDING' },
        { id: 10, name: '박매니저', email: 'park@example.com', role: '매니저', percentage: 15, amount: 720000, paymentStatus: 'PENDING' },
      ],
      createdAt: '2025-02-20T09:00:00Z',
      updatedAt: '2025-03-01T10:00:00Z',
    },
  ]

  const mockSummary: RevenueSplitSummary = {
    totalSplits: 12,
    pendingAmount: 12800000,
    distributedAmount: 48500000,
    totalMembers: 6,
    averageSplit: 5100000,
  }

  async function fetchSplits(status?: string) {
    isLoading.value = true
    try {
      splits.value = await revenueSplitApi.getSplits(status)
    } catch {
      splits.value = status
        ? mockSplits.filter((s) => s.status === status)
        : mockSplits
    } finally {
      isLoading.value = false
    }
  }

  async function createSplit(request: CreateRevenueSplitRequest) {
    try {
      const split = await revenueSplitApi.createSplit(request)
      splits.value.unshift(split)
    } catch {
      const newSplit: RevenueSplit = {
        id: Date.now(),
        ...request,
        status: 'DRAFT',
        members: request.members.map((m, i) => ({
          id: Date.now() + i,
          ...m,
          amount: request.totalAmount * (m.percentage / 100),
          paymentStatus: 'PENDING' as const,
        })),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      splits.value.unshift(newSplit)
    }
  }

  async function deleteSplit(id: number) {
    try {
      await revenueSplitApi.deleteSplit(id)
    } catch {
      // mock fallback
    }
    splits.value = splits.value.filter((s) => s.id !== id)
  }

  async function approveSplit(id: number) {
    try {
      const updated = await revenueSplitApi.approveSplit(id)
      const idx = splits.value.findIndex((s) => s.id === id)
      if (idx !== -1) splits.value[idx] = updated
    } catch {
      const idx = splits.value.findIndex((s) => s.id === id)
      if (idx !== -1) splits.value[idx].status = 'APPROVED'
    }
  }

  async function distributeSplit(id: number) {
    try {
      const updated = await revenueSplitApi.distributeSplit(id)
      const idx = splits.value.findIndex((s) => s.id === id)
      if (idx !== -1) splits.value[idx] = updated
    } catch {
      const idx = splits.value.findIndex((s) => s.id === id)
      if (idx !== -1) {
        splits.value[idx].status = 'DISTRIBUTED'
        splits.value[idx].members.forEach((m) => {
          m.paymentStatus = 'PAID'
          m.paidAt = new Date().toISOString()
        })
      }
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await revenueSplitApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    splits,
    summary,
    isLoading,
    fetchSplits,
    createSplit,
    deleteSplit,
    approveSplit,
    distributeSplit,
    fetchSummary,
  }
})
