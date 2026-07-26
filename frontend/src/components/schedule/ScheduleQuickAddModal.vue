<template>
  <BaseModal
    :model-value="modelValue"
    :title="$t('scheduleView.quickAdd.title')"
    max-width="md"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="space-y-4">
      <!-- 영상 ID -->
      <div>
        <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
          {{ $t('scheduleView.quickAdd.videoId') }}
        </label>
        <input
          v-model.number="form.videoId"
          type="number"
          class="input-field w-full"
          :placeholder="$t('scheduleView.quickAdd.videoIdPlaceholder')"
        />
      </div>

      <!-- 예약 일시 -->
      <div>
        <div class="mb-1 flex items-center justify-between">
          <label class="block text-body font-medium text-gray-700 dark:text-gray-300">
            {{ $t('scheduleView.quickAdd.scheduledAt') }}
          </label>
          <button
            v-if="bestSlot"
            class="flex items-center gap-1 rounded-full bg-success-subtle px-2 py-0.5 text-caption text-success-strong transition hover:opacity-80"
            @click="applySlot(bestSlot)"
          >
            <ClockIcon class="h-3 w-3" />
            {{ $t('scheduleView.recommended') }}: {{ bestSlot.dayLabel }} {{ bestSlot.timeLabel }}
          </button>
        </div>
        <input v-model="form.scheduledAt" type="datetime-local" class="input-field w-full" />
      </div>

      <!-- 플랫폼 -->
      <div>
        <label class="mb-2 block text-body font-medium text-gray-700 dark:text-gray-300">
          {{ $t('scheduleView.quickAdd.selectPlatform') }}
        </label>
        <div class="flex flex-wrap gap-2">
          <label
            v-for="p in allPlatforms"
            :key="p"
            class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 transition-colors"
            :class="
              form.platforms.includes(p)
                ? 'border-primary-300 bg-primary-50 dark:border-primary-800 dark:bg-primary-900/20'
                : 'border-gray-200 hover:border-gray-300 dark:border-gray-700 dark:hover:border-gray-600'
            "
          >
            <input v-model="form.platforms" type="checkbox" :value="p" class="sr-only" />
            <span class="h-2.5 w-2.5 rounded-full" :style="{ backgroundColor: getPlatformColor(p) }" />
            <span class="text-body font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[p].label }}
            </span>
          </label>
        </div>
      </div>

      <!-- 최적 시간대 추천 -->
      <OptimalTimeRecommendation :recommendations="recommendations" @select="applySlot" />

      <!-- 반복 예약 설정 -->
      <RecurrenceSelector v-model="form.recurrence" />
    </div>

    <template #footer>
      <button class="btn-secondary" @click="$emit('update:modelValue', false)">
        {{ $t('scheduleView.quickAdd.cancel') }}
      </button>
      <button class="btn-primary" :disabled="!isValid" @click="submit">
        {{ $t('scheduleView.quickAdd.submit') }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ClockIcon } from '@heroicons/vue/24/outline'
import BaseModal from '@/components/common/BaseModal.vue'
import OptimalTimeRecommendation from '@/components/schedule/OptimalTimeRecommendation.vue'
import RecurrenceSelector from '@/components/schedule/RecurrenceSelector.vue'
import { analyticsApi } from '@/api/analytics'
import { getPlatformColor, toDateTimeLocal } from '@/utils/schedule'
import { PLATFORM_CONFIG, type Platform } from '@/types/channel'
import type { RecurrenceConfig, ScheduleCreateRequest } from '@/types/schedule'
import type { TimeRecommendation } from '@/components/schedule/OptimalTimeRecommendation.vue'

const props = defineProps<{
  modelValue: boolean
  /** 캘린더에서 클릭한 날짜/시간 (없으면 오늘 09:00) */
  initialDate: Date | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [request: ScheduleCreateRequest]
}>()

const allPlatforms: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']

const form = ref({
  videoId: null as number | null,
  scheduledAt: '',
  platforms: [] as Platform[],
  recurrence: { type: 'NONE', interval: 1 } as RecurrenceConfig,
})

// ─── 최적 시간대 추천 (모달 최초 오픈 시 1회 조회) ───
const recommendations = ref<TimeRecommendation[]>([])
const recommendationsLoaded = ref(false)

const bestSlot = computed<TimeRecommendation | null>(() => recommendations.value[0] ?? null)

async function loadRecommendations() {
  if (recommendationsLoaded.value) return
  recommendationsLoaded.value = true
  try {
    const result = await analyticsApi.getOptimalTimes()
    recommendations.value = result.slots.slice(0, 3)
  } catch {
    recommendations.value = []
  }
}

/** 추천 시간을 다음 해당 요일/시각으로 변환해 반영 */
function applySlot(slot: TimeRecommendation) {
  const now = new Date()
  let daysUntil = slot.dayOfWeek - now.getDay()
  if (daysUntil < 0) daysUntil += 7
  if (daysUntil === 0 && slot.hour <= now.getHours()) daysUntil = 7

  const target = new Date(now)
  target.setDate(target.getDate() + daysUntil)
  target.setHours(slot.hour, 0, 0, 0)
  form.value.scheduledAt = toDateTimeLocal(target)
}

const isValid = computed(
  () =>
    form.value.videoId != null &&
    form.value.videoId > 0 &&
    form.value.scheduledAt !== '' &&
    form.value.platforms.length > 0,
)

function submit() {
  if (!isValid.value || form.value.videoId == null) return

  const request: ScheduleCreateRequest = {
    videoId: form.value.videoId,
    scheduledAt: new Date(form.value.scheduledAt).toISOString(),
    platforms: form.value.platforms.map((platform) => ({ platform })),
  }
  if (form.value.recurrence.type !== 'NONE') {
    request.recurrence = form.value.recurrence
  }
  emit('submit', request)
}

// 오픈 시 폼 초기화 + 추천 시간 로드
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const d = props.initialDate ? new Date(props.initialDate) : new Date()
    if (!props.initialDate) d.setHours(9, 0, 0, 0)
    form.value = {
      videoId: null,
      scheduledAt: toDateTimeLocal(d),
      platforms: [],
      recurrence: { type: 'NONE', interval: 1 },
    }
    loadRecommendations()
  },
)
</script>
