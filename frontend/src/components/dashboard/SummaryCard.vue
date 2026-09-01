<template>
  <div
    role="article"
    :aria-label="`${title}: ${formattedValue}${hasChange ? `, 전주 대비 ${changeIcon}${Math.abs(change as number)}${changeType === 'percent' ? '%' : ''}` : ''}`"
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$emit('click')"
  >
    <div class="flex items-center justify-between">
      <p class="text-body font-medium text-gray-500 dark:text-gray-400">{{ title }}</p>
      <div
        class="flex h-9 w-9 items-center justify-center rounded-lg"
        :class="iconBgClass"
      >
        <component :is="icon" class="h-5 w-5" :class="iconColorClass" />
      </div>
    </div>
    <p class="mt-3 text-[1.75rem] font-bold tracking-[-0.04em] text-gray-900 dark:text-gray-100">{{ formattedValue }}</p>
    <div v-if="hasChange" class="mt-1 flex items-center gap-1 text-body">
      <span :class="changeColor">
        {{ changeIcon }}{{ Math.abs(change as number) }}{{ changeType === 'percent' ? '%' : '' }}
      </span>
      <span class="text-gray-400 dark:text-gray-500">{{ $t('dashboard.vsLastWeek') }}</span>
    </div>
    <div v-if="progressBar" class="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        class="h-full rounded-full transition-all"
        :class="(progressPercent ?? 0) <= 20 ? 'bg-error' : 'bg-primary-500'"
        :style="{ width: `${progressPercent ?? 0}%` }"
      />
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n({ useScope: 'global' })

type CardColor = 'blue' | 'green' | 'rose' | 'purple' | 'gray'

const colorMap: Record<CardColor, { bg: string; text: string }> = {
  blue: { bg: 'bg-info-subtle', text: 'text-info-strong' },
  green: { bg: 'bg-success-subtle', text: 'text-success-strong' },
  rose: { bg: 'bg-error-subtle', text: 'text-error-strong' },
  purple: { bg: 'bg-info-subtle', text: 'text-info-strong' },
  gray: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-500 dark:text-gray-400' },
}

const props = withDefaults(
  defineProps<{
    title: string
    /**
     * 표시할 수치. **`null` 이면 측정 불가**이며 숫자 대신 문구를 그린다.
     *
     * 구독 증가처럼 수집하는 플랫폼이 없을 수 있는 지표에 쓴다. `?? 0` 으로 채우면
     * "0명" 이라는 관측이 되어 실제로 0명이 늘어난 경우와 구분되지 않는다.
     */
    value: number | null
    /**
     * 증감. **`null` 이면 비교 불가**(이전 기간 데이터 없음)이며 표시하지 않는다.
     *
     * `undefined` 와 같이 다뤄야 한다. 서버가 `null` 을 주는데 `!== undefined` 로만
     * 검사하면 `Math.abs(null)` 이 `0` 이 되어 **"↑0%"** 라는 없는 사실이 화면에 뜬다.
     */
    change?: number | null
    changeType?: 'percent' | 'number'
    icon: Component
    color?: CardColor
    format?: 'number' | 'compact'
    progressBar?: boolean
    progressPercent?: number
  }>(),
  {
    change: undefined,
    changeType: 'percent',
    color: 'gray',
    format: 'compact',
    progressBar: false,
    progressPercent: undefined,
  },
)

defineEmits<{
  click: []
}>()

const iconBgClass = computed(() => colorMap[props.color].bg)
const iconColorClass = computed(() => colorMap[props.color].text)

const formattedValue = computed(() => {
  const value = props.value
  // 미측정은 숫자로 그리지 않는다. NaN·Infinity 도 같이 막는다.
  if (typeof value !== 'number' || !Number.isFinite(value)) return t('analyticsView.notMeasured')
  if (props.format === 'compact') {
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
    if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  }
  return value.toLocaleString()
})

/**
 * \uc99d\uac10\uc744 \ud45c\uc2dc\ud560 \uc218 \uc788\ub294\uac00.
 *
 * `null`(\ube44\uad50 \ubd88\uac00)\uacfc `undefined`(\uac12 \uc5c6\uc74c)\ub97c **\uac19\uc774** \uac78\ub7ec\uc57c \ud55c\ub2e4. `!== undefined` \ub9cc
 * \uac80\uc0ac\ud558\uba74 \uc11c\ubc84\uac00 \uc900 `null` \uc774 \ud1b5\uacfc\ud574 `Math.abs(null) === 0` \uc774 \ub418\uace0, \ud654\uba74\uc5d0\ub294
 * **"\u21910%"** \ub77c\ub294 \uce21\uc815\ub41c \uc801 \uc5c6\ub294 \uac12\uc774 \ub72c\ub2e4.
 */
const hasChange = computed(() => typeof props.change === 'number' && Number.isFinite(props.change))

const changeIcon = computed(() => {
  if (!hasChange.value) return ''
  if (props.changeType === 'number') return props.change! >= 0 ? '+' : ''
  return props.change! >= 0 ? '\u2191' : '\u2193'
})

const changeColor = computed(() => {
  if (!hasChange.value) return ''
  return props.change! >= 0 ? 'text-success-strong' : 'text-error-strong'
})
</script>
