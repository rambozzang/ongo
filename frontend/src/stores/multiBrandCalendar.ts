import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { multiBrandCalendarApi } from '@/api/multiBrandCalendar'
import type { Brand, BrandScheduleItem, CalendarConflict, CreateBrandRequest, CreateScheduleRequest } from '@/types/multiBrandCalendar'

function generateMockBrands(): Brand[] {
  return [
    { id: 1, name: '뷰티 브랜드 A', color: 'PINK', category: '뷰티', assignedEditors: ['editor1'], totalScheduled: 12, totalPublished: 8, isActive: true, createdAt: '2025-01-01' },
    { id: 2, name: '테크 리뷰 B', color: 'BLUE', category: '테크', assignedEditors: ['editor2'], totalScheduled: 8, totalPublished: 6, isActive: true, createdAt: '2025-01-15' },
    { id: 3, name: '푸드 채널 C', color: 'GREEN', category: '음식', assignedEditors: ['editor1', 'editor3'], totalScheduled: 15, totalPublished: 12, isActive: true, createdAt: '2025-02-01' },
    { id: 4, name: '교육 채널 D', color: 'PURPLE', category: '교육', assignedEditors: ['editor2'], totalScheduled: 6, totalPublished: 4, isActive: false, createdAt: '2025-02-10' },
  ]
}

function generateMockSchedule(): BrandScheduleItem[] {
  const today = new Date()
  return [
    { id: 1, brandId: 1, brandName: '뷰티 브랜드 A', brandColor: 'PINK', title: '봄 신상 메이크업', platform: 'YOUTUBE', scheduledAt: new Date(today.getTime() + 86400000).toISOString(), status: 'SCHEDULED', assignedTo: 'editor1' },
    { id: 2, brandId: 2, brandName: '테크 리뷰 B', brandColor: 'BLUE', title: '갤럭시 S26 리뷰', platform: 'YOUTUBE', scheduledAt: new Date(today.getTime() + 86400000).toISOString(), status: 'SCHEDULED', assignedTo: 'editor2' },
    { id: 3, brandId: 3, brandName: '푸드 채널 C', brandColor: 'GREEN', title: '집밥 레시피 #45', platform: 'TIKTOK', scheduledAt: new Date(today.getTime() + 172800000).toISOString(), status: 'DRAFT', assignedTo: 'editor1' },
    { id: 4, brandId: 1, brandName: '뷰티 브랜드 A', brandColor: 'PINK', title: '데일리 스킨케어', platform: 'INSTAGRAM', scheduledAt: new Date(today.getTime() + 259200000).toISOString(), status: 'SCHEDULED', assignedTo: 'editor1' },
  ]
}

export const useMultiBrandCalendarStore = defineStore('multiBrandCalendar', () => {
  const brands = ref<Brand[]>([])
  const schedule = ref<BrandScheduleItem[]>([])
  const conflicts = ref<CalendarConflict[]>([])
  const isLoading = ref(false)

  const activeBrands = computed(() => brands.value.filter((b) => b.isActive))
  const totalScheduledThisWeek = computed(() => schedule.value.filter((s) => s.status === 'SCHEDULED').length)

  async function fetchBrands() {
    try {
      brands.value = await multiBrandCalendarApi.getBrands()
    } catch {
      brands.value = generateMockBrands()
    }
  }

  async function fetchSchedule(startDate: string, endDate: string, brandId?: number) {
    isLoading.value = true
    try {
      schedule.value = await multiBrandCalendarApi.getSchedule(startDate, endDate, brandId)
    } catch {
      schedule.value = generateMockSchedule()
      if (brandId) schedule.value = schedule.value.filter((s) => s.brandId === brandId)
    } finally {
      isLoading.value = false
    }
  }

  async function createBrand(request: CreateBrandRequest) {
    try {
      const created = await multiBrandCalendarApi.createBrand(request)
      brands.value.push(created)
    } catch {
      const mock: Brand = { id: Date.now(), ...request, assignedEditors: request.assignedEditors ?? [], totalScheduled: 0, totalPublished: 0, isActive: true, createdAt: new Date().toISOString(), logoUrl: undefined }
      brands.value.push(mock)
    }
  }

  async function deleteBrand(id: number) {
    try { await multiBrandCalendarApi.deleteBrand(id) } catch { /* fallback */ }
    brands.value = brands.value.filter((b) => b.id !== id)
  }

  async function createScheduleItem(request: CreateScheduleRequest) {
    try {
      const created = await multiBrandCalendarApi.createSchedule(request)
      schedule.value.push(created)
    } catch {
      const brand = brands.value.find((b) => b.id === request.brandId)
      const mock: BrandScheduleItem = { id: Date.now(), brandId: request.brandId, brandName: brand?.name ?? '', brandColor: brand?.color ?? 'BLUE', title: request.title, platform: request.platform, scheduledAt: request.scheduledAt, status: 'DRAFT', assignedTo: request.assignedTo }
      schedule.value.push(mock)
    }
  }

  async function deleteScheduleItem(id: number) {
    try { await multiBrandCalendarApi.deleteSchedule(id) } catch { /* fallback */ }
    schedule.value = schedule.value.filter((s) => s.id !== id)
  }

  return { brands, schedule, conflicts, isLoading, activeBrands, totalScheduledThisWeek, fetchBrands, fetchSchedule, createBrand, deleteBrand, createScheduleItem, deleteScheduleItem }
})
