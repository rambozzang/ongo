import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { PlanType } from '@/types/subscription'

export function useAuth() {
  const authStore = useAuthStore()

  const isAuthenticated = computed(() => authStore.isAuthenticated)
  const user = computed(() => authStore.user)
  const currentPlan = computed<PlanType>(() => authStore.user?.planType ?? 'FREE')

  function hasPlan(minPlan: PlanType): boolean {
    const planOrder: PlanType[] = ['FREE', 'STARTER', 'PRO', 'BUSINESS']
    const currentIndex = planOrder.indexOf(currentPlan.value)
    const requiredIndex = planOrder.indexOf(minPlan)
    return currentIndex >= requiredIndex
  }

  return {
    isAuthenticated,
    user,
    currentPlan,
    hasPlan,
    login: authStore.login,
    logout: authStore.logout,
  }
}
