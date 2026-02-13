import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest } from '@/types/user'
import { authApi } from '@/api/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const accessToken = ref<string | null>(localStorage.getItem('accessToken'))

  const isAuthenticated = computed(() => !!accessToken.value)

  async function login(provider: 'google' | 'kakao', request: LoginRequest) {
    const response = await authApi.login(provider, request)
    accessToken.value = response.accessToken
    localStorage.setItem('accessToken', response.accessToken)
    localStorage.setItem('refreshToken', response.refreshToken)
    user.value = response.user

    if (response.isNewUser) {
      await router.push('/onboarding')
    } else {
      await router.push('/dashboard')
    }
  }

  async function fetchProfile() {
    if (!accessToken.value) return
    try {
      user.value = await authApi.getProfile()
    } catch {
      // 인증 실패 시 상태만 정리 (router.push 하지 않음 — 라우터 가드가 리다이렉트 처리)
      user.value = null
      accessToken.value = null
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
    }
  }

  async function devLogin() {
    const response = await authApi.devLogin()
    accessToken.value = response.accessToken
    localStorage.setItem('accessToken', response.accessToken)
    localStorage.setItem('refreshToken', response.refreshToken)
    user.value = response.user
    await router.push('/dashboard')
  }

  function logout() {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    // 전체 페이지 새로고침으로 이동 — 모든 Pinia 인메모리 상태 확실히 초기화
    window.location.href = '/login'
  }

  async function initialize() {
    if (accessToken.value && !user.value) {
      await fetchProfile()
    }
  }

  return {
    user,
    accessToken,
    isAuthenticated,
    login,
    devLogin,
    fetchProfile,
    logout,
    initialize,
  }
})
