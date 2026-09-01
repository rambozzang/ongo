import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CreditBalance, CreditPackage, CreditTransaction } from '@/types/credit'
import type { PageResponse } from '@/types/api'
import { creditApi } from '@/api/credit'

export const useCreditStore = defineStore('credit', () => {
  const balance = ref<CreditBalance | null>(null)
  const transactions = ref<PageResponse<CreditTransaction> | null>(null)
  const isLoadingBalance = ref(false)
  const isLoadingTransactions = ref(false)
  const balanceError = ref<string | null>(null)
  /*
   * 구매 가능한 패키지. **서버가 준 값만 담는다.**
   *
   * 예전에는 화면이 `CREDIT_PACKAGES` 상수를 그렸다. 결제 금액은 서버가 enum 에서
   * 계산하므로, 한쪽만 바뀌면 사용자가 본 금액과 청구액이 갈린다. 조회 전·실패 시에는
   * `null` 로 두어 화면이 **오래된 숫자를 대신 그리지 않게** 한다.
   */
  const packages = ref<CreditPackage[] | null>(null)
  const isLoadingPackages = ref(false)
  const packagesError = ref<string | null>(null)
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

  async function fetchPackages() {
    isLoadingPackages.value = true
    packagesError.value = null
    try {
      const items = await creditApi.getPackages()
      packages.value = items.map((item) => ({
        // 결제에 보내는 식별자는 서버가 준 enum 이름 그대로다. 화면 문구를 보내면 안 된다.
        key: item.name as CreditPackage['key'],
        name: item.displayName,
        credits: item.credits,
        price: item.price,
        pricePerCredit: item.pricePerCredit,
        validDays: item.validDays,
      }))
    } catch (error) {
      // 실패를 빈 목록으로 바꾸지 않는다. 빈 목록은 "살 수 있는 것이 없다"는 다른 사실이다.
      packages.value = null
      packagesError.value = error instanceof Error ? error.message : '크레딧 패키지를 불러오지 못했습니다.'
    } finally {
      isLoadingPackages.value = false
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
    packages,
    isLoadingPackages,
    packagesError,
    fetchPackages,
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
