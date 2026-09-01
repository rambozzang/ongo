<template>
  <div class="-mx-6 overflow-x-auto px-6 scrollbar-dark">
    <table class="w-full min-w-[600px] tablet:min-w-[800px] text-body">
      <thead>
        <tr class="border-b-2 border-gray-200 dark:border-gray-700">
          <th class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-4 text-left text-body-xs font-semibold uppercase tracking-wider text-gray-600 dark:text-gray-400">
            기능
          </th>
          <th
            v-for="plan in displayPlans"
            :key="plan.type"
            class="px-4 py-4 text-center"
            :class="getPlanHeaderClass(plan.type)"
          >
            <div class="space-y-2">
              <div class="text-body font-bold uppercase tracking-wider" :class="getPlanNameClass(plan.type)">
                {{ plan.name }}
              </div>
              <div class="text-h2 font-bold" :class="getPlanPriceClass(plan.type)">
                {{ getDisplayPrice(plan).amount === 0 ? '무료' : '₩' + getDisplayPrice(plan).amount.toLocaleString() }}
                <span v-if="getDisplayPrice(plan).amount > 0" class="text-body-xs font-normal">{{ getDisplayPrice(plan).label }}</span>
              </div>
              <div v-if="plan.type === currentPlan" class="flex justify-center">
                <span class="inline-flex items-center rounded-full px-3 py-1 text-body-xs font-medium" :class="getCurrentPlanBadgeClass(plan.type)">
                  현재 플랜
                </span>
              </div>
            </div>
          </th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <!-- 월간 업로드 수 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            월간 업로드 수
          </td>
          <td v-for="plan in displayPlans" :key="`upload-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ plan.maxUploadsPerMonth === -1 ? '무제한' : plan.maxUploadsPerMonth + '회' }}</span>
          </td>
        </tr>

        <!-- 저장 용량 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            저장 용량
          </td>
          <td v-for="plan in displayPlans" :key="`storage-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ formatStorage(plan.storageMb) }}</span>
          </td>
        </tr>

        <!-- 연동 채널 수 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            연동 채널 수
          </td>
          <td v-for="plan in displayPlans" :key="`platforms-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ plan.maxPlatforms === -1 ? '무제한' : plan.maxPlatforms + '개' }}</span>
          </td>
        </tr>

        <!-- AI 무료 크레딧 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            AI 무료 크레딧
          </td>
          <td v-for="plan in displayPlans" :key="`credits-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ plan.freeAiCredits.toLocaleString() }}크레딧</span>
          </td>
        </tr>

        <!-- 분석 데이터 보관 기간 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            분석 데이터 보관
          </td>
          <td v-for="plan in displayPlans" :key="`analytics-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ formatAnalyticsPeriod(plan.analyticsPeriodDays) }}</span>
          </td>
        </tr>

        <!-- 예약 게시 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            예약 게시
          </td>
          <td v-for="plan in displayPlans" :key="`schedule-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <CheckIcon v-if="plan.maxScheduleDays > 0" class="mx-auto h-6 w-6 text-success-strong" />
            <XMarkIcon v-else class="mx-auto h-6 w-6 text-gray-300 dark:text-gray-600" />
          </td>
        </tr>

        <!-- 팀 멤버 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            팀 멤버
          </td>
          <td v-for="plan in displayPlans" :key="`team-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ plan.teamMembers === 0 ? '1명' : plan.teamMembers + '명' }}</span>
          </td>
        </tr>

        <!-- Action Buttons -->
        <tr class="bg-gray-50 dark:bg-gray-800/50">
          <td class="sticky left-0 z-10 bg-gray-50 dark:bg-gray-800/50 px-4 py-4 border-r border-gray-200 dark:border-gray-700" />
          <td v-for="plan in displayPlans" :key="`action-${plan.type}`" class="px-4 py-4 text-center" :class="getCellClass(plan.type)">
            <button
              v-if="plan.type !== currentPlan"
              class="w-full rounded-lg px-4 py-2 text-body font-medium transition-colors"
              :class="[
                getActionButtonClass(plan.type),
                isPaymentBlocked(plan) ? 'cursor-not-allowed opacity-60' : '',
              ]"
              :disabled="isPaymentBlocked(plan)"
              :title="isPaymentBlocked(plan) ? paymentDisabledReason : undefined"
              @click="$emit('select-plan', plan.type)"
            >
              {{ getActionButtonLabel(plan.type) }}
            </button>
            <span v-else class="inline-block rounded-lg px-4 py-2 text-body font-medium" :class="getCurrentBadgeTextClass(plan.type)">
              사용 중
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { type PlanType, type Plan } from '@/types/subscription'

interface Props {
  currentPlan?: PlanType
  plans?: Plan[]
  billingCycle?: 'MONTHLY' | 'YEARLY'
  /** 서버가 결제를 열 수 있다고 확인했는지. 모르면 유료 업그레이드를 막는다. */
  paymentEnabled?: boolean
  /** 잠긴 유료 업그레이드에 마우스·보조기술로 전달할 설명 */
  paymentDisabledReason?: string
}

const props = withDefaults(defineProps<Props>(), {
  currentPlan: undefined,
  plans: undefined,
  billingCycle: 'MONTHLY',
  // 결제 capability를 모르면 유료 전환을 열지 않는다. 서버 판정이 빠진 재사용 경로가
  // 실수로 PG 결제창을 여는 fail-open을 막고, 부모가 명시적으로 true를 전달할 때만 연다.
  paymentEnabled: false,
  paymentDisabledReason: '온라인 결제를 사용할 수 없어 유료 플랜으로 업그레이드할 수 없습니다.',
})

/*
 * **서버가 준 플랜만 그린다.**
 *
 * 예전에는 `props.plans ?? PLANS` 로 클라이언트 상수를 대신 썼다. 가격과 한도는 서버가
 * 결제 기준으로 삼는 값이라, 상수가 뒤처지면 사용자가 본 금액과 청구액이 갈린다.
 * 목록이 없으면 표를 그리지 않는다 — 오래된 숫자를 보여 주는 것이 빈 표보다 나쁘다.
 */
const displayPlans = computed<Plan[]>(() => props.plans ?? [])

function getDisplayPrice(plan: Plan): { amount: number; label: string } {
  if (props.billingCycle === 'YEARLY' && plan.yearlyPrice > 0) {
    return { amount: plan.yearlyPrice, label: '/년' }
  }
  return { amount: plan.price, label: '/월' }
}

defineEmits<{
  (e: 'select-plan', planType: PlanType): void
}>()

function formatStorage(mb: number): string {
  if (mb >= 1024) return (mb / 1024) + 'GB'
  return mb + 'MB'
}

function formatAnalyticsPeriod(days: number): string {
  if (days < 0) return '무제한'
  if (days === 0) return '미지원'
  return `${days}일`
}

function getPlanHeaderClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'bg-gray-50 dark:bg-gray-700/30',
      STARTER: 'bg-info-subtle',
      PRO: 'bg-info-subtle',
      BUSINESS: 'bg-warning-subtle',
    }
    return colorMap[planType]
  }
  return ''
}

function getPlanNameClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'text-gray-700 dark:text-gray-300',
      STARTER: 'text-info-strong',
      PRO: 'text-info-strong',
      BUSINESS: 'text-warning-strong',
    }
    return colorMap[planType]
  }
  return 'text-gray-600 dark:text-gray-400'
}

function getPlanPriceClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'text-gray-900 dark:text-gray-100',
      STARTER: 'text-info-strong',
      PRO: 'text-info-strong',
      BUSINESS: 'text-warning-strong',
    }
    return colorMap[planType]
  }
  return 'text-gray-700 dark:text-gray-300'
}

function getCurrentPlanBadgeClass(planType: PlanType): string {
  const colorMap: Record<PlanType, string> = {
    FREE: 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300',
    STARTER: 'bg-info-subtle text-info-strong',
    PRO: 'bg-info-subtle text-info-strong',
    BUSINESS: 'bg-warning-subtle text-warning-strong',
  }
  return colorMap[planType]
}

function getCellClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'bg-gray-50/50 dark:bg-gray-700/20',
      STARTER: 'bg-info-subtle',
      PRO: 'bg-info-subtle',
      BUSINESS: 'bg-warning-subtle',
    }
    return colorMap[planType]
  }
  return 'text-gray-600 dark:text-gray-400'
}

function getActionButtonLabel(planType: PlanType): string {
  if (!props.currentPlan) return '선택'
  const currentIdx = displayPlans.value.findIndex((p) => p.type === props.currentPlan)
  const targetIdx = displayPlans.value.findIndex((p) => p.type === planType)
  if (targetIdx > currentIdx) return '업그레이드'
  return '다운그레이드'
}

/**
 * 결제 준비가 안 된 동안에는 실제 결제가 필요한 업그레이드만 잠근다.
 *
 * Free 전환·다운그레이드는 결제 없이 서버가 처리하므로 같이 막으면 안 된다. 부모의
 * `selectPlan` 과 같은 플랜 순서를 사용해, 버튼이 활성인데 클릭 후 무반응이 되는 상태를
 * 없애고 서버가 비활성인 이유도 버튼 자체에 남긴다.
 */
function isPaymentBlocked(plan: Plan): boolean {
  if (props.paymentEnabled || plan.price === 0) return false
  if (!props.currentPlan) return true

  const currentIdx = displayPlans.value.findIndex((item) => item.type === props.currentPlan)
  const targetIdx = displayPlans.value.findIndex((item) => item.type === plan.type)
  return targetIdx > currentIdx
}

function getActionButtonClass(planType: PlanType): string {
  if (!props.currentPlan) {
    return 'bg-primary-600 text-white hover:bg-primary-700'
  }
  const currentIdx = displayPlans.value.findIndex((p) => p.type === props.currentPlan)
  const targetIdx = displayPlans.value.findIndex((p) => p.type === planType)

  if (targetIdx > currentIdx) {
    // Upgrade button - use primary
    return 'bg-primary-600 text-white hover:bg-primary-700'
  } else {
    // Downgrade button - use secondary
    return 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
  }
}

function getCurrentBadgeTextClass(planType: PlanType): string {
  const colorMap: Record<PlanType, string> = {
    FREE: 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300',
    STARTER: 'bg-info-subtle text-info-strong',
    PRO: 'bg-info-subtle text-info-strong',
    BUSINESS: 'bg-warning-subtle text-warning-strong',
  }
  return colorMap[planType]
}
</script>
