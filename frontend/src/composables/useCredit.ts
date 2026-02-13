import { computed } from 'vue'
import { useCreditStore } from '@/stores/credit'
import { useNotificationStore } from '@/stores/notification'

export function useCredit() {
  const creditStore = useCreditStore()
  const notificationStore = useNotificationStore()

  const balance = computed(() => creditStore.totalBalance)
  const isLow = computed(() => creditStore.isLow)

  async function checkAndUse(required: number, featureName: string): Promise<boolean> {
    await creditStore.fetchBalance()

    if (!creditStore.hasEnoughCredits(required)) {
      notificationStore.warning(
        `AI 크레딧이 부족합니다. ${featureName}에 ${required} 크레딧이 필요합니다. (잔여: ${balance.value})`,
      )
      return false
    }

    return true
  }

  return {
    balance,
    isLow,
    checkAndUse,
    fetchBalance: creditStore.fetchBalance,
    hasEnoughCredits: creditStore.hasEnoughCredits,
  }
}
