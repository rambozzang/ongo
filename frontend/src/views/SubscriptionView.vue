<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('subscription.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('subscription.description') }}
        </p>
      </div>
    </div>

    <PageGuide :title="$t('subscription.pageGuideTitle')" :items="($tm('subscription.pageGuide') as string[])" />

    <LoadingSpinner v-if="subscriptionStore.loading && !subscription" full-page />

    <template v-else>
      <!-- Section 1: Current Plan Card -->
      <div class="card mb-6">
        <div class="flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
          <div>
            <div class="mb-1 flex items-center gap-2">
              <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.currentPlan') }}</h2>
              <span
                v-if="subscription"
                class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                :class="subscriptionStatusClass"
              >
                {{ subscriptionStatusLabel }}
              </span>
            </div>
            <div v-if="subscription" class="space-y-1">
              <p class="text-2xl font-bold text-primary-600">
                {{ currentPlanInfo?.name ?? subscription.planType }}
                <span class="text-base font-normal text-gray-500 dark:text-gray-400">
                  {{ currentPlanInfo ? formatPrice(currentPlanInfo.price) : '' }}
                  <template v-if="currentPlanInfo && currentPlanInfo.price > 0">{{ $t('subscription.perMonth') }}</template>
                </span>
              </p>
              <p v-if="subscription.nextBillingDate" class="text-sm text-gray-500 dark:text-gray-400">
                <CalendarIcon class="mr-1 inline h-4 w-4" />
                {{ $t('subscription.nextBillingDate') }}: {{ formatDate(subscription.nextBillingDate) }}
              </p>
              <p v-if="currentPlanInfo" class="text-sm text-gray-500 dark:text-gray-400">
                <ArrowUpTrayIcon class="mr-1 inline h-4 w-4" />
                {{ $t('subscription.uploads') }}:
                <template v-if="currentPlanInfo.maxUploadsPerMonth === -1">{{ $t('subscription.unlimited') }}</template>
                <template v-else>{{ $t('subscription.uploadsPerMonth', { count: currentPlanInfo.maxUploadsPerMonth }) }}</template>
              </p>
            </div>
            <p v-else class="text-sm text-gray-500 dark:text-gray-400">{{ $t('subscription.noSubscriptionInfo') }}</p>
          </div>
          <div v-if="subscription" class="flex gap-2">
            <button class="btn-primary" @click="showPlanComparison">
              <ArrowPathIcon class="mr-1.5 h-4 w-4" />
              {{ $t('subscription.changePlan') }}
            </button>
            <button
              v-if="subscription.status === 'ACTIVE' && subscription.planType !== 'FREE'"
              class="btn-danger"
              @click="showCancelModal = true"
            >
              {{ $t('subscription.cancelSubscription') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Section 2: Usage Statistics -->
      <div v-if="currentPlanInfo" class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.usageStatus') }}</h2>

        <div class="space-y-4">
          <!-- Monthly Uploads Usage -->
          <UsageProgressBar
            :label="$t('subscription.monthlyUploads')"
            :current="usageData.uploadsThisMonth"
            :max="currentPlanInfo.maxUploadsPerMonth"
            :unit="$t('subscription.unitTimes')"
          />

          <!-- Storage Usage -->
          <UsageProgressBar
            :label="$t('subscription.storage')"
            :current="formatStorageValue(usageData.storageUsedMb, currentPlanInfo.storageMb)"
            :max="formatStorageValue(currentPlanInfo.storageMb, currentPlanInfo.storageMb)"
            :unit="formatStorageUnit(currentPlanInfo.storageMb)"
          />

          <!-- Connected Platforms -->
          <UsageProgressBar
            :label="$t('subscription.connectedChannels')"
            :current="connectedPlatformCount"
            :max="currentPlanInfo.maxPlatforms"
            :unit="$t('subscription.unitCount')"
          />
        </div>
      </div>

      <!-- Section 3: AI Credit Section -->
      <div class="card mb-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            <SparklesIcon class="mr-1.5 inline h-5 w-5 text-primary-600" />
            {{ $t('subscription.aiCredits') }}
          </h2>
          <button class="btn-primary" @click="showCreditModal = true">
            <PlusIcon class="mr-1.5 h-4 w-4" />
            {{ $t('subscription.chargeCredits') }}
          </button>
        </div>

        <div v-if="creditBalance">
          <div class="mb-2 flex items-end justify-between">
            <div>
              <span class="text-3xl font-bold" :class="creditStore.isLow ? 'text-red-600' : 'text-gray-900 dark:text-gray-100'">
                {{ creditStore.totalBalance.toLocaleString() }}
              </span>
              <span class="ml-1 text-sm text-gray-500 dark:text-gray-400">
                / {{ creditBalance.freeMonthly.toLocaleString() }} ({{ $t('subscription.monthlyFree') }})
              </span>
            </div>
            <span class="text-xs text-gray-400 dark:text-gray-500">
              {{ $t('subscription.freeResetDate') }}: {{ formatDate(creditBalance.freeResetDate) }}
            </span>
          </div>

          <!-- Progress Bar -->
          <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="creditStore.isLow ? 'bg-red-500' : 'bg-primary-600'"
              :style="{ width: creditPercentage + '%' }"
            />
          </div>
          <div class="mt-2 flex justify-between text-xs text-gray-500 dark:text-gray-400">
            <span>{{ $t('subscription.freeRemaining') }}: {{ creditBalance.freeRemaining.toLocaleString() }}</span>
            <span>{{ $t('subscription.purchasedRemaining') }}: {{ creditBalance.purchasedBalance.toLocaleString() }}</span>
          </div>

          <!-- Low credit warning -->
          <div
            v-if="creditStore.isLow"
            class="mt-3 flex items-center gap-2 rounded-lg bg-red-50 dark:bg-red-900/20 px-3 py-2 text-sm text-red-700 dark:text-red-400"
          >
            <ExclamationTriangleIcon class="h-4 w-4 flex-shrink-0" />
            {{ $t('subscription.lowCreditWarning') }}
          </div>
        </div>
        <div v-else class="py-4 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ $t('subscription.noCreditInfo') }}
        </div>
      </div>

      <!-- Section 4: Credit Usage History Table -->
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.creditHistory') }}</h2>

        <div class="overflow-x-auto">
          <table v-if="creditTransactions && creditTransactions.content.length > 0" class="w-full text-sm">
            <thead>
              <tr class="border-b border-gray-200 dark:border-gray-700 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <th class="px-4 py-3">{{ $t('subscription.table.date') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.type') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.feature') }}</th>
                <th class="px-4 py-3 text-right">{{ $t('subscription.table.credits') }}</th>
                <th class="px-4 py-3 text-right">{{ $t('subscription.table.remaining') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
              <tr
                v-for="tx in creditTransactions.content"
                :key="tx.id"
                class="hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <td class="whitespace-nowrap px-4 py-3 text-gray-600 dark:text-gray-300">
                  {{ formatDateTime(tx.createdAt) }}
                </td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="creditTransactionTypeClass(tx.type)"
                  >
                    {{ creditTransactionTypeLabel(tx.type) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-gray-700 dark:text-gray-300">
                  {{ tx.feature ?? '-' }}
                </td>
                <td class="whitespace-nowrap px-4 py-3 text-right font-medium" :class="tx.type === 'DEDUCT' ? 'text-red-600' : 'text-green-600'">
                  {{ tx.type === 'DEDUCT' ? '-' : '+' }}{{ Math.abs(tx.amount).toLocaleString() }}
                </td>
                <td class="whitespace-nowrap px-4 py-3 text-right text-gray-600 dark:text-gray-300">
                  {{ tx.balanceAfter.toLocaleString() }}
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="py-8 text-center text-sm text-gray-500 dark:text-gray-400">
            {{ $t('subscription.noCreditHistory') }}
          </div>
        </div>

        <!-- Pagination -->
        <div
          v-if="creditTransactions && creditTransactions.totalPages > 1"
          class="mt-4 flex items-center justify-between border-t border-gray-100 dark:border-gray-700 pt-4"
        >
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('subscription.totalCount', { count: creditTransactions.totalElements.toLocaleString() }) }}
          </p>
          <div class="flex gap-1">
            <button
              class="rounded-lg px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!creditTransactions.hasPrevious"
              @click="loadCreditTransactions(creditTransactions!.page - 1)"
            >
              {{ $t('action.previous') }}
            </button>
            <span class="px-3 py-1.5 text-xs text-gray-500 dark:text-gray-400">
              {{ creditTransactions.page + 1 }} / {{ creditTransactions.totalPages }}
            </span>
            <button
              class="rounded-lg px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!creditTransactions.hasNext"
              @click="loadCreditTransactions(creditTransactions!.page + 1)"
            >
              {{ $t('action.next') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Section 5: Plan Comparison Table -->
      <div id="plan-comparison" class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.planComparison') }}</h2>

        <PlanComparisonTable
          :current-plan="subscription?.planType"
          @select-plan="selectPlan"
        />
      </div>

      <!-- Section 6: Payment History -->
      <div class="card">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.paymentHistory') }}</h2>

        <div class="overflow-x-auto">
          <table v-if="paymentList && paymentList.content.length > 0" class="w-full text-sm">
            <thead>
              <tr class="border-b border-gray-200 dark:border-gray-700 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <th class="px-4 py-3">{{ $t('subscription.table.date') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.item') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.description') }}</th>
                <th class="px-4 py-3 text-right">{{ $t('subscription.table.amount') }}</th>
                <th class="px-4 py-3 text-center">{{ $t('subscription.table.status') }}</th>
                <th class="px-4 py-3 text-center">{{ $t('subscription.table.receipt') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
              <tr
                v-for="payment in paymentList.content"
                :key="payment.id"
                class="hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <td class="whitespace-nowrap px-4 py-3 text-gray-600 dark:text-gray-300">
                  {{ formatDateTime(payment.paidAt) }}
                </td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="payment.type === 'SUBSCRIPTION' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' : 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400'"
                  >
                    {{ payment.type === 'SUBSCRIPTION' ? $t('subscription.typeSubscription') : $t('subscription.typeCredit') }}
                  </span>
                </td>
                <td class="px-4 py-3 text-gray-700 dark:text-gray-300">
                  {{ payment.description }}
                </td>
                <td class="whitespace-nowrap px-4 py-3 text-right font-medium text-gray-900 dark:text-gray-100">
                  {{ formatPrice(payment.amount) }}
                </td>
                <td class="px-4 py-3 text-center">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="paymentStatusClass(payment.status)"
                  >
                    {{ paymentStatusLabel(payment.status) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-center">
                  <a
                    v-if="payment.receiptUrl"
                    :href="payment.receiptUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="text-primary-600 hover:text-primary-700 hover:underline"
                  >
                    <DocumentTextIcon class="mx-auto h-4 w-4" />
                  </a>
                  <span v-else class="text-gray-300 dark:text-gray-600">-</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="py-8 text-center text-sm text-gray-500 dark:text-gray-400">
            {{ $t('subscription.noPaymentHistory') }}
          </div>
        </div>

        <!-- Payment Pagination -->
        <div
          v-if="paymentList && paymentList.totalPages > 1"
          class="mt-4 flex items-center justify-between border-t border-gray-100 dark:border-gray-700 pt-4"
        >
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('subscription.totalCount', { count: paymentList.totalElements.toLocaleString() }) }}
          </p>
          <div class="flex gap-1">
            <button
              class="rounded-lg px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!paymentList.hasPrevious"
              @click="loadPayments(paymentList!.page - 1)"
            >
              {{ $t('action.previous') }}
            </button>
            <span class="px-3 py-1.5 text-xs text-gray-500 dark:text-gray-400">
              {{ paymentList.page + 1 }} / {{ paymentList.totalPages }}
            </span>
            <button
              class="rounded-lg px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!paymentList.hasNext"
              @click="loadPayments(paymentList!.page + 1)"
            >
              {{ $t('action.next') }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- Plan Change Confirmation Modal -->
    <ConfirmModal
      v-model="showChangePlanModal"
      :title="$t('subscription.changePlanTitle')"
      :message="changePlanMessage"
      :confirm-text="$t('subscription.changePlanConfirm')"
      @confirm="confirmChangePlan"
    />

    <!-- Cancel Subscription Confirmation Modal -->
    <ConfirmModal
      v-model="showCancelModal"
      :title="$t('subscription.cancelTitle')"
      :message="$t('subscription.cancelMessage')"
      :confirm-text="$t('subscription.cancelConfirmText')"
      danger
      @confirm="confirmCancel"
    />

    <!-- Credit Purchase Modal -->
    <CreditPurchaseModal
      v-model="showCreditModal"
      @purchase="handleCreditPurchase"
    />

    <!-- Payment Modal -->
    <PaymentModal
      v-model="showPaymentModal"
      :target-plan="targetPlan ?? 'FREE'"
      :price="targetPlanInfo?.price ?? 0"
      @confirm="handlePaymentConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  CalendarIcon,
  ArrowUpTrayIcon,
  ArrowPathIcon,
  SparklesIcon,
  PlusIcon,
  ExclamationTriangleIcon,
  DocumentTextIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import UsageProgressBar from '@/components/subscription/UsageProgressBar.vue'
import PlanComparisonTable from '@/components/subscription/PlanComparisonTable.vue'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import PaymentModal from '@/components/subscription/PaymentModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useSubscriptionStore } from '@/stores/subscription'
import { useCreditStore } from '@/stores/credit'
import { useChannelStore } from '@/stores/channel'
import { useNotificationStore } from '@/stores/notification'
import { PLANS, type PlanType } from '@/types/subscription'
import type { CreditPackage } from '@/types/credit'
import { creditApi } from '@/api/credit'
import { subscriptionApi } from '@/api/subscription'
import { useLocale } from '@/composables/useLocale'

const subscriptionStore = useSubscriptionStore()
const creditStore = useCreditStore()
const channelStore = useChannelStore()
const notification = useNotificationStore()
const { t } = useLocale()

const { subscription } = storeToRefs(subscriptionStore)
const { balance: creditBalance, transactions: creditTransactions } = storeToRefs(creditStore)
const { payments: paymentList } = storeToRefs(subscriptionStore)
const { channels } = storeToRefs(channelStore)

// Modal states
const showChangePlanModal = ref(false)
const showCancelModal = ref(false)
const showCreditModal = ref(false)
const showPaymentModal = ref(false)
const targetPlan = ref<PlanType | null>(null)

// Usage data from real API
const usageData = ref({
  uploadsThisMonth: 0,
  storageUsedMb: 0,
})
const usageLoading = ref(false)

// Connected platforms count derived from the channel store
const connectedPlatformCount = computed(() => channels.value.length)

// Computed
const currentPlanInfo = computed(() => {
  if (!subscription.value) return null
  return PLANS.find((p) => p.type === subscription.value!.planType) ?? null
})

const targetPlanInfo = computed(() => {
  if (!targetPlan.value) return null
  return PLANS.find((p) => p.type === targetPlan.value) ?? null
})

const creditPercentage = computed(() => {
  if (!creditBalance.value) return 0
  const total = creditBalance.value.freeMonthly + creditBalance.value.purchasedBalance
  if (total === 0) return 0
  return Math.min(100, Math.round((creditStore.totalBalance / total) * 100))
})

const subscriptionStatusClass = computed(() => {
  if (!subscription.value) return ''
  const classes: Record<string, string> = {
    ACTIVE: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400',
    FREE: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400',
    CANCELLED: 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300',
    PAST_DUE: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400',
  }
  return classes[subscription.value.status] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300'
})

const subscriptionStatusLabel = computed(() => {
  if (!subscription.value) return ''
  const labelKeys: Record<string, string> = {
    ACTIVE: 'subscription.statusActive',
    FREE: 'subscription.statusFree',
    CANCELLED: 'subscription.statusCancelled',
    PAST_DUE: 'subscription.statusPastDue',
  }
  const key = labelKeys[subscription.value.status]
  return key ? t(key) : subscription.value.status
})

const changePlanMessage = computed(() => {
  if (!targetPlan.value) return ''
  const target = PLANS.find((p) => p.type === targetPlan.value)
  if (!target) return ''
  const current = currentPlanInfo.value
  if (!current) return t('subscription.changePlanSimple', { plan: target.name })

  if (target.price > current.price) {
    return t('subscription.upgradeMessage', { current: current.name, target: target.name, price: formatPrice(target.price) })
  }
  const targetPrice = target.price === 0 ? t('subscription.free') : formatPrice(target.price) + t('subscription.perMonth')
  return t('subscription.downgradeMessage', { current: current.name, target: target.name, price: targetPrice })
})

// Helpers
function formatPrice(amount: number): string {
  if (amount === 0) return t('subscription.free')
  return '\u20A9' + amount.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }) + ' ' + date.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatStorageUnit(mb: number): string {
  if (mb >= 1024) return 'GB'
  return 'MB'
}

function formatStorageValue(valueMb: number, maxMb: number): number {
  if (maxMb >= 1024) return Math.round((valueMb / 1024) * 10) / 10
  return valueMb
}

function creditTransactionTypeClass(type: string): string {
  const classes: Record<string, string> = {
    DEDUCT: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400',
    CHARGE: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400',
    FREE_RESET: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400',
  }
  return classes[type] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300'
}

function creditTransactionTypeLabel(type: string): string {
  const labelKeys: Record<string, string> = {
    DEDUCT: 'subscription.creditTypeDeduct',
    CHARGE: 'subscription.creditTypeCharge',
    FREE_RESET: 'subscription.creditTypeFreeReset',
  }
  const key = labelKeys[type]
  return key ? t(key) : type
}

function paymentStatusClass(status: string): string {
  const classes: Record<string, string> = {
    COMPLETED: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400',
    FAILED: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400',
    REFUNDED: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400',
  }
  return classes[status] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300'
}

function paymentStatusLabel(status: string): string {
  const labelKeys: Record<string, string> = {
    COMPLETED: 'subscription.paymentCompleted',
    FAILED: 'subscription.paymentFailed',
    REFUNDED: 'subscription.paymentRefunded',
  }
  const key = labelKeys[status]
  return key ? t(key) : status
}

// Actions
function showPlanComparison() {
  const el = document.getElementById('plan-comparison')
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
  }
}

function selectPlan(plan: PlanType) {
  targetPlan.value = plan
  // For FREE plan or downgrades, show confirmation modal
  // For upgrades, show payment modal
  const currentIdx = PLANS.findIndex((p) => p.type === subscription.value?.planType)
  const targetIdx = PLANS.findIndex((p) => p.type === plan)

  if (targetIdx > currentIdx) {
    // Upgrade - show payment modal
    showPaymentModal.value = true
  } else {
    // Downgrade or same - show confirmation
    showChangePlanModal.value = true
  }
}

async function confirmChangePlan() {
  if (!targetPlan.value) return
  try {
    await subscriptionStore.changePlan(targetPlan.value)
    notification.success(t('subscription.changePlanSuccess'))
    targetPlan.value = null
    await creditStore.fetchBalance()
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : t('subscription.changePlanError'))
  }
}

async function handlePaymentConfirm() {
  if (!targetPlan.value) return
  try {
    await subscriptionStore.changePlan(targetPlan.value)
    notification.success(t('subscription.upgradeSuccess'))
    showPaymentModal.value = false
    targetPlan.value = null
    await creditStore.fetchBalance()
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : t('subscription.changePlanError'))
  }
}

async function confirmCancel() {
  try {
    await subscriptionStore.cancelSubscription()
    notification.success(t('subscription.cancelSuccess'))
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : t('subscription.cancelError'))
  }
}

async function handleCreditPurchase(pkg: CreditPackage) {
  try {
    const updatedBalance = await creditApi.purchase({ packageType: pkg.name, paymentMethod: 'CARD' })
    creditStore.balance = updatedBalance
    notification.success(t('subscription.creditChargeSuccess'))
    await Promise.all([
      creditStore.fetchTransactions(0, 20),
      subscriptionStore.fetchPayments(0, 20),
    ])
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : t('subscription.creditChargeError'))
  }
}

function loadCreditTransactions(page: number) {
  creditStore.fetchTransactions(page, 20)
}

function loadPayments(page: number) {
  subscriptionStore.fetchPayments(page, 20)
}

// Load usage data from the API
async function fetchUsage() {
  usageLoading.value = true
  try {
    const data = await subscriptionApi.getUsage()
    usageData.value = {
      uploadsThisMonth: data.uploadsThisMonth ?? 0,
      storageUsedMb: data.storageUsedMb ?? 0,
    }
  } catch {
    // Non-critical: keep defaults if usage endpoint is unavailable
  } finally {
    usageLoading.value = false
  }
}

// Init
onMounted(() => {
  Promise.all([
    subscriptionStore.fetchSubscription(),
    creditStore.fetchBalance(),
    creditStore.fetchTransactions(0, 20),
    subscriptionStore.fetchPayments(0, 20),
    channelStore.fetchChannels(),
    fetchUsage(),
  ])
})
</script>
