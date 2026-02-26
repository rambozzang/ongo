import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { sponsorshipTrackerApi } from '@/api/sponsorshipTracker'
import type { Sponsorship, SponsorshipSummary, CreateSponsorshipRequest, CreateDeliverableRequest } from '@/types/sponsorshipTracker'

function generateMockSponsorships(): Sponsorship[] {
  return [
    { id: 1, brandName: '삼성전자', contactName: '이마케팅', contactEmail: 'lee@samsung.com', status: 'IN_PROGRESS', dealValue: 5000000, currency: 'KRW', startDate: '2025-02-01', endDate: '2025-03-31', deliverables: [{ id: 1, sponsorshipId: 1, type: 'DEDICATED_VIDEO', title: '갤럭시 S26 리뷰', dueDate: '2025-03-15', isCompleted: false, platform: 'YOUTUBE' }, { id: 2, sponsorshipId: 1, type: 'SHORT_FORM', title: '갤럭시 숏폼', dueDate: '2025-03-20', isCompleted: false, platform: 'TIKTOK' }], paymentStatus: 'PENDING', paidAmount: 0, createdAt: '2025-02-01', updatedAt: '2025-02-20' },
    { id: 2, brandName: '나이키', contactName: '김브랜드', contactEmail: 'kim@nike.com', status: 'DELIVERED', dealValue: 3000000, currency: 'KRW', startDate: '2025-01-15', endDate: '2025-02-28', deliverables: [{ id: 3, sponsorshipId: 2, type: 'INTEGRATED', title: '운동 루틴 + 나이키', dueDate: '2025-02-20', isCompleted: true, completedAt: '2025-02-18', platform: 'YOUTUBE' }], paymentStatus: 'INVOICED', paidAmount: 0, createdAt: '2025-01-15', updatedAt: '2025-02-18' },
    { id: 3, brandName: '쿠팡', contactName: '박딜', contactEmail: 'park@coupang.com', status: 'PAID', dealValue: 2000000, currency: 'KRW', startDate: '2025-01-01', endDate: '2025-01-31', deliverables: [{ id: 4, sponsorshipId: 3, type: 'STORY', title: '쿠팡 할인 소식', dueDate: '2025-01-20', isCompleted: true, completedAt: '2025-01-19', platform: 'INSTAGRAM' }], paymentStatus: 'PAID', paidAmount: 2000000, createdAt: '2025-01-01', updatedAt: '2025-02-05' },
  ]
}

function generateMockSummary(): SponsorshipSummary {
  return { totalDeals: 8, activeDeals: 3, totalRevenue: 15000000, pendingPayments: 5000000, completionRate: 72, avgDealValue: 3500000, upcomingDeadlines: [], revenueByMonth: [{ month: '2025-01', revenue: 4000000 }, { month: '2025-02', revenue: 6000000 }] }
}

export const useSponsorshipTrackerStore = defineStore('sponsorshipTracker', () => {
  const sponsorships = ref<Sponsorship[]>([])
  const summary = ref<SponsorshipSummary | null>(null)
  const isLoading = ref(false)

  const activeDeals = computed(() => sponsorships.value.filter((s) => ['INQUIRY', 'NEGOTIATION', 'CONTRACTED', 'IN_PROGRESS'].includes(s.status)))
  const completedDeals = computed(() => sponsorships.value.filter((s) => ['DELIVERED', 'PAID'].includes(s.status)))
  const totalRevenue = computed(() => sponsorships.value.reduce((sum, s) => sum + s.paidAmount, 0))

  async function fetchSponsorships(status?: string) {
    isLoading.value = true
    try { sponsorships.value = await sponsorshipTrackerApi.getSponsorships(status) } catch { sponsorships.value = generateMockSponsorships(); if (status) sponsorships.value = sponsorships.value.filter((s) => s.status === status) } finally { isLoading.value = false }
  }

  async function fetchSummary() {
    try { summary.value = await sponsorshipTrackerApi.getSummary() } catch { summary.value = generateMockSummary() }
  }

  async function createSponsorship(request: CreateSponsorshipRequest) {
    try {
      const created = await sponsorshipTrackerApi.createSponsorship(request)
      sponsorships.value.unshift(created)
    } catch {
      const mock: Sponsorship = { id: Date.now(), ...request, brandLogo: undefined, currency: request.currency ?? 'KRW', status: 'INQUIRY', deliverables: [], paymentStatus: 'PENDING', paidAmount: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
      sponsorships.value.unshift(mock)
    }
  }

  async function deleteSponsorship(id: number) {
    try { await sponsorshipTrackerApi.deleteSponsorship(id) } catch { /* fallback */ }
    sponsorships.value = sponsorships.value.filter((s) => s.id !== id)
  }

  async function addDeliverable(request: CreateDeliverableRequest) {
    try {
      const created = await sponsorshipTrackerApi.createDeliverable(request)
      const sp = sponsorships.value.find((s) => s.id === request.sponsorshipId)
      if (sp) sp.deliverables.push(created)
    } catch {
      const mock = { id: Date.now(), ...request, isCompleted: false }
      const sp = sponsorships.value.find((s) => s.id === request.sponsorshipId)
      if (sp) sp.deliverables.push(mock)
    }
  }

  return { sponsorships, summary, isLoading, activeDeals, completedDeals, totalRevenue, fetchSponsorships, fetchSummary, createSponsorship, deleteSponsorship, addDeliverable }
})
