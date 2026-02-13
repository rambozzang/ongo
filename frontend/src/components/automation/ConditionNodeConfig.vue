<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  type ConditionGroupType,
  type ConditionOperator,
  CONDITION_OPERATOR_OPTIONS,
} from '@/types/automation'

const groupType = defineModel<ConditionGroupType>('groupType', { required: true })
const field = defineModel<string | undefined>('field', { required: true })
const operator = defineModel<ConditionOperator | undefined>('operator', { required: true })
const value = defineModel<string | undefined>('value', { required: true })
const expression = defineModel<string | undefined>('expression', { required: true })

const useSpel = ref(!!expression.value)

watch(useSpel, (v) => {
  if (v) {
    field.value = undefined
    operator.value = undefined
    value.value = undefined
  } else {
    expression.value = undefined
  }
})

const COMMON_FIELDS = [
  { value: '#video.views', label: '조회수' },
  { value: '#video.likes', label: '좋아요' },
  { value: '#video.engagementRate', label: '참여율' },
  { value: '#video.platform', label: '플랫폼' },
  { value: '#video.status', label: '상태' },
  { value: '#channel.subscriberCount', label: '구독자 수' },
  { value: '#analytics.totalViews', label: '총 조회수' },
]
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
      <span class="w-2 h-2 rounded-full bg-amber-500"></span>
      조건 설정
    </h3>

    <!-- AND/OR Toggle -->
    <div>
      <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">그룹 연산자</label>
      <div class="flex gap-2">
        <button
          v-for="type in (['AND', 'OR'] as const)"
          :key="type"
          class="flex-1 py-2 rounded-lg text-sm font-medium transition-colors"
          :class="
            groupType === type
              ? type === 'AND'
                ? 'bg-blue-600 text-white'
                : 'bg-purple-600 text-white'
              : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
          "
          @click="groupType = type"
        >
          {{ type }}
        </button>
      </div>
    </div>

    <!-- Mode toggle -->
    <div class="flex items-center gap-2">
      <label class="relative inline-flex items-center cursor-pointer">
        <input
          type="checkbox"
          v-model="useSpel"
          class="sr-only peer"
        />
        <div class="w-9 h-5 bg-gray-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:peer-focus:ring-primary-800 rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all dark:after:border-gray-600 peer-checked:bg-primary-600"></div>
      </label>
      <span class="text-xs text-gray-600 dark:text-gray-400">SpEL 표현식 사용</span>
    </div>

    <!-- SpEL Expression Mode -->
    <div v-if="useSpel" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">SpEL 표현식</label>
        <textarea
          :value="expression ?? ''"
          rows="3"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm font-mono"
          placeholder="#video.views > 10000 AND #video.platform == 'YOUTUBE'"
          @input="expression = ($event.target as HTMLTextAreaElement).value"
        />
      </div>
      <div class="text-xs text-gray-500 dark:text-gray-400 space-y-1">
        <p class="font-medium">사용 가능한 변수:</p>
        <p><code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">#video</code> - views, likes, engagementRate, platform</p>
        <p><code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">#channel</code> - subscriberCount</p>
        <p><code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">#analytics</code> - totalViews</p>
      </div>
    </div>

    <!-- Simple Field/Operator/Value Mode -->
    <div v-else class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">필드</label>
        <select
          :value="field ?? ''"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @change="field = ($event.target as HTMLSelectElement).value"
        >
          <option value="">필드 선택</option>
          <option v-for="f in COMMON_FIELDS" :key="f.value" :value="f.value">
            {{ f.label }} ({{ f.value }})
          </option>
        </select>
      </div>

      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">연산자</label>
        <select
          :value="operator ?? 'EQUALS'"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @change="operator = ($event.target as HTMLSelectElement).value as ConditionOperator"
        >
          <option v-for="op in CONDITION_OPERATOR_OPTIONS" :key="op.value" :value="op.value">
            {{ op.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">값</label>
        <input
          type="text"
          :value="value ?? ''"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          placeholder="비교값 입력"
          @input="value = ($event.target as HTMLInputElement).value"
        />
      </div>
    </div>
  </div>
</template>
