import { defineStore } from 'pinia'
import { ref } from 'vue'
import { audienceApi } from '@/api/audience'
import type { AudienceProfile, AudienceSegment } from '@/types/audience'

export const useAudienceStore = defineStore('audience', () => {
  const profiles = ref<AudienceProfile[]>([])
  const segments = ref<AudienceSegment[]>([])
  const totalProfiles = ref(0)
  const loading = ref(false)
  const profilesError = ref<string | null>(null)
  const segmentsError = ref<string | null>(null)

  async function loadProfiles(sortBy = 'engagement_score', page = 0, size = 20) {
    loading.value = true
    profilesError.value = null
    try {
      const result = await audienceApi.getProfiles(sortBy, page, size)
      profiles.value = result.profiles
      totalProfiles.value = result.total
    } catch (error) {
      profilesError.value = error instanceof Error ? error.message : '팬 프로필을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  async function loadSegments() {
    loading.value = true
    segmentsError.value = null
    try {
      segments.value = await audienceApi.getSegments()
    } catch (error) {
      segmentsError.value = error instanceof Error ? error.message : '팬 세그먼트를 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  async function createSegment(data: { name: string; description?: string; conditions?: string; autoUpdate?: boolean }) {
    const segment = await audienceApi.createSegment(data)
    segments.value.push(segment)
    return segment
  }

  async function deleteSegment(id: number) {
    await audienceApi.deleteSegment(id)
    segments.value = segments.value.filter((s) => s.id !== id)
  }

  return {
    profiles,
    segments,
    totalProfiles,
    loading,
    profilesError,
    segmentsError,
    loadProfiles,
    loadSegments,
    createSegment,
    deleteSegment,
  }
})
