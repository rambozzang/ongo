<template>
  <div class="space-y-6">
    <!-- Header / Bio Section -->
    <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
      <div class="flex items-start gap-4">
        <div
          class="flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary-400 to-primary-600 text-2xl font-bold text-white"
        >
          {{ kit.title?.charAt(0)?.toUpperCase() || 'M' }}
        </div>
        <div class="flex-1">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ kit.title }}</h2>
          <span
            class="mt-1 inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
            :class="templateBadgeClass"
          >
            {{ $t(`mediaKit.templateStyles.${kit.templateStyle}`) }}
          </span>
          <p class="mt-2 text-sm leading-relaxed text-gray-600 dark:text-gray-400">{{ kit.bio }}</p>
        </div>
      </div>
    </div>

    <!-- Platform Stats -->
    <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.platformStats') }}
      </h3>
      <div class="grid gap-3 tablet:grid-cols-3">
        <div
          v-for="stat in kit.platforms"
          :key="stat.platform"
          class="rounded-lg border border-gray-100 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="mb-2 flex items-center gap-2">
            <span class="text-lg">{{ platformIcon(stat.platform) }}</span>
            <span class="text-sm font-medium capitalize text-gray-900 dark:text-gray-100">
              {{ stat.platform }}
            </span>
          </div>
          <div class="space-y-1">
            <div class="flex items-center justify-between text-xs">
              <span class="text-gray-500 dark:text-gray-400">{{ $t('mediaKit.followers') }}</span>
              <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(stat.followers) }}</span>
            </div>
            <div class="flex items-center justify-between text-xs">
              <span class="text-gray-500 dark:text-gray-400">{{ $t('mediaKit.avgViews') }}</span>
              <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(stat.avgViews) }}</span>
            </div>
            <div class="flex items-center justify-between text-xs">
              <span class="text-gray-500 dark:text-gray-400">{{ $t('mediaKit.engagement') }}</span>
              <span class="font-semibold text-primary-600 dark:text-primary-400">{{ stat.engagementRate }}%</span>
            </div>
            <div class="flex items-center justify-between text-xs">
              <span class="text-gray-500 dark:text-gray-400">{{ $t('mediaKit.growth') }}</span>
              <span
                class="font-semibold"
                :class="stat.growthRate >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
              >
                {{ stat.growthRate >= 0 ? '+' : '' }}{{ stat.growthRate }}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Demographics -->
    <div
      v-if="kit.demographics && kit.demographics.length > 0"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.demographics') }}
      </h3>
      <div class="space-y-3">
        <div v-for="(demo, idx) in kit.demographics" :key="idx">
          <div class="mb-1 flex items-center justify-between text-xs">
            <span class="text-gray-700 dark:text-gray-300">
              {{ demo.ageGroup }}
              <span class="ml-1 text-gray-400 dark:text-gray-500">
                ({{ $t(`mediaKit.gender.${demo.gender}`) }})
              </span>
            </span>
            <span class="font-medium text-gray-900 dark:text-gray-100">{{ demo.percentage }}%</span>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="genderBarClass(demo.gender)"
              :style="{ width: `${demo.percentage}%` }"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Top Content -->
    <div
      v-if="kit.topContent && kit.topContent.length > 0"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.topContent') }}
      </h3>
      <div class="space-y-2">
        <div
          v-for="(content, idx) in kit.topContent"
          :key="idx"
          class="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="flex items-center gap-3">
            <span class="flex h-6 w-6 items-center justify-center rounded-full bg-primary-100 text-xs font-bold text-primary-700 dark:bg-primary-900/30 dark:text-primary-400">
              {{ idx + 1 }}
            </span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ content.title }}</span>
          </div>
          <span class="text-sm font-semibold text-gray-600 dark:text-gray-400">
            {{ formatNumber(content.views) }} {{ $t('mediaKit.views') }}
          </span>
        </div>
      </div>
    </div>

    <!-- Rate Cards -->
    <div
      v-if="kit.rateCards && kit.rateCards.length > 0"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.rateCards') }}
      </h3>
      <div class="grid gap-3 tablet:grid-cols-2">
        <div
          v-for="(card, idx) in kit.rateCards"
          :key="idx"
          class="rounded-lg border border-gray-100 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="mb-1 text-xs font-medium text-primary-600 dark:text-primary-400">
            {{ $t(`mediaKit.rateCardTypes.${card.type}`) }}
          </div>
          <div class="text-lg font-bold text-gray-900 dark:text-gray-100">
            {{ formatKRW(card.price) }}
          </div>
          <p v-if="card.description" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {{ card.description }}
          </p>
        </div>
      </div>
    </div>

    <!-- Campaign Results -->
    <div
      v-if="kit.campaignResults && kit.campaignResults.length > 0"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.campaignResults') }}
      </h3>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700">
              <th class="pb-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('mediaKit.brand') }}</th>
              <th class="pb-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('mediaKit.campaign') }}</th>
              <th class="pb-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('mediaKit.campaignViews') }}</th>
              <th class="pb-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('mediaKit.campaignEngagement') }}</th>
              <th class="pb-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">ROI</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="result in kit.campaignResults"
              :key="result.id"
              class="border-b border-gray-100 last:border-0 dark:border-gray-800"
            >
              <td class="py-2 font-medium text-gray-900 dark:text-gray-100">{{ result.brandName }}</td>
              <td class="py-2 text-gray-600 dark:text-gray-400">{{ result.campaignName }}</td>
              <td class="py-2 text-right text-gray-900 dark:text-gray-100">{{ formatNumber(result.views) }}</td>
              <td class="py-2 text-right text-gray-900 dark:text-gray-100">{{ formatNumber(result.engagement) }}</td>
              <td class="py-2 text-right font-semibold text-primary-600 dark:text-primary-400">{{ result.roi }}x</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Contact -->
    <div
      v-if="kit.contactEmail"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.contact') }}
      </h3>
      <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
        <EnvelopeIcon class="h-4 w-4" />
        <a :href="`mailto:${kit.contactEmail}`" class="text-primary-600 hover:underline dark:text-primary-400">
          {{ kit.contactEmail }}
        </a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EnvelopeIcon } from '@heroicons/vue/24/outline'
import type { MediaKit } from '@/types/mediaKit'

const props = defineProps<{
  kit: MediaKit
}>()

const templateBadgeClass = computed(() => {
  const map: Record<string, string> = {
    MODERN: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    CLASSIC: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
    MINIMAL: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    CREATIVE: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  }
  return map[props.kit.templateStyle] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

function platformIcon(platform: string): string {
  const icons: Record<string, string> = {
    youtube: '\u25B6\uFE0F',
    instagram: '\uD83D\uDCF7',
    tiktok: '\uD83C\uDFB5',
    naverclip: '\uD83D\uDCCE',
  }
  return icons[platform.toLowerCase()] || '\uD83C\uDF10'
}

function genderBarClass(gender: string): string {
  const map: Record<string, string> = {
    FEMALE: 'bg-pink-500 dark:bg-pink-400',
    MALE: 'bg-blue-500 dark:bg-blue-400',
    OTHER: 'bg-purple-500 dark:bg-purple-400',
  }
  return map[gender] ?? 'bg-gray-500 dark:bg-gray-400'
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}
</script>
