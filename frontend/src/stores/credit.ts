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
  const balanceError = ref<string | null>(null)
  const transactionsError = ref<string | null>(null)

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

  const usedToday = computed(() => {
    const startOfToday = new Date()
    startOfToday.setHours(0, 0, 0, 0)
    return (transactions.value?.content ?? [])
      .filter((transaction) => transaction.type === 'DEDUCT' && new Date(transaction.createdAt) >= startOfToday)
      .reduce((total, transaction) => total + Math.abs(transaction.amount), 0)
  })

  async function fetchBalance() {
    isLoadingBalance.value = true
    balanceError.value = null
    try {
      balance.value = await creditApi.getBalance()
    } catch (error) {
      balanceError.value = error instanceof Error ? error.message : '크레딧 잔액을 불러오지 못했습니다.'
    } finally {
      isLoadingBalance.value = false
    }
  }

  async function fetchTransactions(page = 0, size = 20) {
    isLoadingTransactions.value = true
    transactionsError.value = null
    try {
      transactions.value = await creditApi.getTransactions({ page, size })
    } catch (error) {
      transactionsError.value = error instanceof Error ? error.message : '크레딧 사용 내역을 불러오지 못했습니다.'
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
    balanceError,
    transactionsError,
    totalBalance,
    isLow,
    usedToday,
    fetchBalance,
    fetchTransactions,
    hasEnoughCredits,
  }
})
