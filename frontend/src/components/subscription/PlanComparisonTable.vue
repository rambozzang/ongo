<template>
  <div class="-mx-6 overflow-x-auto px-6">
    <table class="w-full min-w-[800px] text-sm">
      <thead>
        <tr class="border-b-2 border-gray-200 dark:border-gray-700">
          <th class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-4 text-left text-xs font-semibold uppercase tracking-wider text-gray-600 dark:text-gray-400">
            기능
          </th>
          <th
            v-for="plan in displayPlans"
            :key="plan.type"
            class="px-4 py-4 text-center"
            :class="getPlanHeaderClass(plan.type)"
          >
            <div class="space-y-2">
              <div class="text-sm font-bold uppercase tracking-wider" :class="getPlanNameClass(plan.type)">
                {{ plan.name }}
              </div>
              <div class="text-xl font-bold" :class="getPlanPriceClass(plan.type)">
                {{ getDisplayPrice(plan).amount === 0 ? '무료' : '₩' + getDisplayPrice(plan).amount.toLocaleString() }}
                <span v-if="getDisplayPrice(plan).amount > 0" class="text-xs font-normal">{{ getDisplayPrice(plan).label }}</span>
              </div>
              <div v-if="plan.type === currentPlan" class="flex justify-center">
                <span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-medium" :class="getCurrentPlanBadgeClass(plan.type)">
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

        <!-- 동시 업로드 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            동시 업로드
          </td>
          <td v-for="plan in displayPlans" :key="`concurrent-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ getConcurrentUploads(plan.type) }}개</span>
          </td>
        </tr>

        <!-- 분석 기능 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            분석 기능
          </td>
          <td v-for="plan in displayPlans" :key="`analytics-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <span class="font-medium">{{ getAnalyticsLevel(plan.type) }}</span>
          </td>
        </tr>

        <!-- 예약 게시 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            예약 게시
          </td>
          <td v-for="plan in displayPlans" :key="`schedule-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <CheckIcon v-if="plan.maxScheduleDays > 0" class="mx-auto h-6 w-6 text-green-500" />
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

        <!-- 우선 지원 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            우선 지원
          </td>
          <td v-for="plan in displayPlans" :key="`priority-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <CheckIcon v-if="['PRO', 'BUSINESS'].includes(plan.type)" class="mx-auto h-6 w-6 text-green-500" />
            <XMarkIcon v-else class="mx-auto h-6 w-6 text-gray-300 dark:text-gray-600" />
          </td>
        </tr>

        <!-- API 접근 -->
        <tr>
          <td class="sticky left-0 z-10 bg-white dark:bg-gray-800 px-4 py-3 font-medium text-gray-700 dark:text-gray-300 border-r border-gray-200 dark:border-gray-700">
            API 접근
          </td>
          <td v-for="plan in displayPlans" :key="`api-${plan.type}`" class="px-4 py-3 text-center" :class="getCellClass(plan.type)">
            <CheckIcon v-if="plan.type === 'BUSINESS'" class="mx-auto h-6 w-6 text-green-500" />
            <XMarkIcon v-else class="mx-auto h-6 w-6 text-gray-300 dark:text-gray-600" />
          </td>
        </tr>

        <!-- Action Buttons -->
        <tr class="bg-gray-50 dark:bg-gray-800/50">
          <td class="sticky left-0 z-10 bg-gray-50 dark:bg-gray-800/50 px-4 py-4 border-r border-gray-200 dark:border-gray-700" />
          <td v-for="plan in displayPlans" :key="`action-${plan.type}`" class="px-4 py-4 text-center" :class="getCellClass(plan.type)">
            <button
              v-if="plan.type !== currentPlan"
              class="w-full rounded-lg px-4 py-2 text-sm font-medium transition-colors"
              :class="getActionButtonClass(plan.type)"
              @click="$emit('select-plan', plan.type)"
            >
              {{ getActionButtonLabel(plan.type) }}
            </button>
            <span v-else class="inline-block rounded-lg px-4 py-2 text-sm font-medium" :class="getCurrentBadgeTextClass(plan.type)">
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
import { PLANS, type PlanType, type Plan } from '@/types/subscription'

interface Props {
  currentPlan?: PlanType
  plans?: Plan[]
  billingCycle?: 'MONTHLY' | 'YEARLY'
}

const props = withDefaults(defineProps<Props>(), {
  currentPlan: undefined,
  plans: undefined,
  billingCycle: 'MONTHLY',
})

const displayPlans = computed(() => props.plans ?? PLANS)

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

function getConcurrentUploads(planType: PlanType): number {
  const map: Record<PlanType, number> = {
    FREE: 1,
    STARTER: 3,
    PRO: 5,
    BUSINESS: 10,
  }
  return map[planType]
}

function getAnalyticsLevel(planType: PlanType): string {
  const map: Record<PlanType, string> = {
    FREE: '기본',
    STARTER: '상세',
    PRO: '고급',
    BUSINESS: '프리미엄',
  }
  return map[planType]
}

function getPlanHeaderClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'bg-gray-50 dark:bg-gray-700/30',
      STARTER: 'bg-blue-50 dark:bg-blue-900/20',
      PRO: 'bg-purple-50 dark:bg-purple-900/20',
      BUSINESS: 'bg-amber-50 dark:bg-amber-900/20',
    }
    return colorMap[planType]
  }
  return ''
}

function getPlanNameClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'text-gray-700 dark:text-gray-300',
      STARTER: 'text-blue-700 dark:text-blue-400',
      PRO: 'text-purple-700 dark:text-purple-400',
      BUSINESS: 'text-amber-700 dark:text-amber-400',
    }
    return colorMap[planType]
  }
  return 'text-gray-600 dark:text-gray-400'
}

function getPlanPriceClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'text-gray-900 dark:text-gray-100',
      STARTER: 'text-blue-700 dark:text-blue-400',
      PRO: 'text-purple-700 dark:text-purple-400',
      BUSINESS: 'text-amber-700 dark:text-amber-400',
    }
    return colorMap[planType]
  }
  return 'text-gray-700 dark:text-gray-300'
}

function getCurrentPlanBadgeClass(planType: PlanType): string {
  const colorMap: Record<PlanType, string> = {
    FREE: 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300',
    STARTER: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400',
    PRO: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400',
    BUSINESS: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400',
  }
  return colorMap[planType]
}

function getCellClass(planType: PlanType): string {
  if (planType === props.currentPlan) {
    const colorMap: Record<PlanType, string> = {
      FREE: 'bg-gray-50/50 dark:bg-gray-700/20',
      STARTER: 'bg-blue-50/50 dark:bg-blue-900/10',
      PRO: 'bg-purple-50/50 dark:bg-purple-900/10',
      BUSINESS: 'bg-amber-50/50 dark:bg-amber-900/10',
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
    STARTER: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400',
    PRO: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400',
    BUSINESS: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400',
  }
  return colorMap[planType]
}
</script>
