import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CreditBalance, CreditTransaction } from '@/types/credit'
import type { PageResponse } from '@/types/api'
import { creditApi } from '@/api/credit'

export const useCreditStore = defineStore('credit', () => {
  const balance = ref<CreditBalance | null>(null)
  const transactions = ref<PageResponse<CreditTransaction> | null>(null)
  const isLoadingBalance = ref(false)
  const isLoadingTransactions = ref(false)

  // Backwards-compatible loading ref
  const loading = isLoadingTransactions

  const totalBalance = computed(() => {
    if (!balance.value) return 0
    return balance.value.totalBalance
  })

  const isLow = computed(() => {
    if (!balance.value) return false
    const total = balance.value.freeMonthly + balance.value.purchasedBalance
    return total > 0 && totalBalance.value / total <= 0.2
  })

  async function fetchBalance() {
    isLoadingBalance.value = true
    try {
      balance.value = await creditApi.getBalance()
    } catch {
      // silently fail for credit display
    } finally {
      isLoadingBalance.value = false
    }
  }

  async function fetchTransactions(page = 0, size = 20) {
    isLoadingTransactions.value = true
    try {
      transactions.value = await creditApi.getTransactions({ page, size })
    } catch {
      // silently fail for transaction display
    } finally {
      isLoadingTransactions.value = false
    }
  }

  function hasEnoughCredits(required: number): boolean {
    return totalBalance.value >= required
  }

  return {
    balance,
    transactions,
    loading,
    isLoadingBalance,
    isLoadingTransactions,
    totalBalance,
    isLow,
    fetchBalance,
    fetchTransactions,
    hasEnoughCredits,
  }
})
