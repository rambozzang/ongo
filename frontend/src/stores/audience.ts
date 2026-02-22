import { defineStore } from 'pinia'
import { ref } from 'vue'
import { audienceApi } from '@/api/audience'
import type { AudienceProfile, AudienceSegment } from '@/types/audience'

export const useAudienceStore = defineStore('audience', () => {
  const profiles = ref<AudienceProfile[]>([])
  const segments = ref<AudienceSegment[]>([])
  const totalProfiles = ref(0)
  const loading = ref(false)

  async function loadProfiles(sortBy = 'engagement_score', page = 0, size = 20) {
    loading.value = true
    try {
      const result = await audienceApi.getProfiles(sortBy, page, size)
      profiles.value = result.profiles
      totalProfiles.value = result.total
    } finally {
      loading.value = false
    }
  }

  async function loadSegments() {
    loading.value = true
    try {
      segments.value = await audienceApi.getSegments()
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

  return { profiles, segments, totalProfiles, loading, loadProfiles, loadSegments, createSegment, deleteSegment }
})
