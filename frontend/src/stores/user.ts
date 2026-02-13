import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserProfile } from '@/types/user'
import { useAuthStore } from './auth'
import { authApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const loading = ref(false)

  async function updateProfile(profile: UserProfile) {
    loading.value = true
    try {
      await authApi.updateProfile(profile)
      const authStore = useAuthStore()
      await authStore.fetchProfile()
    } catch (e) {
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    updateProfile,
  }
})
