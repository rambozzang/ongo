<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="-translate-y-2 opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="-translate-y-2 opacity-0"
  >
    <div v-if="open" class="card mb-4">
      <div class="flex flex-wrap items-center gap-4">
        <!-- 플랫폼 필터 -->
        <div>
          <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('scheduleView.filterPlatform') }}
          </label>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="p in platformOptions"
              :key="p.value"
              class="rounded-full px-2.5 py-1 text-xs font-medium transition-colors"
              :class="
                platform === p.value
                  ? 'text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
              "
              :style="platform === p.value ? { backgroundColor: p.color } : {}"
              @click="$emit('update:platform', platform === p.value ? null : p.value)"
            >
              {{ p.label }}
            </button>
          </div>
        </div>

        <!-- 상태 필터 -->
        <div>
          <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('scheduleView.filterStatus') }}
          </label>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="s in statusOptions"
              :key="s.value"
              class="rounded-full px-2.5 py-1 text-xs font-medium transition-colors"
              :class="
                status === s.value
                  ? s.activeClass
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
              "
              @click="$emit('update:status', status === s.value ? null : s.value)"
            >
              {{ s.label }}
            </button>
          </div>
        </div>

        <!-- 필터 초기화 -->
        <button
          v-if="activeCount > 0"
          class="ml-auto text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
          @click="$emit('reset')"
        >
          {{ $t('scheduleView.filterReset') }}
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useScheduleLabels } from '@/composables/useScheduleLabels'
import type { ScheduleStatus } from '@/types/schedule'
import type { Platform } from '@/types/channel'

const props = defineProps<{
  open: boolean
  platform: Platform | null
  status: ScheduleStatus | null
}>()

defineEmits<{
  'update:platform': [value: Platform | null]
  'update:status': [value: ScheduleStatus | null]
  reset: []
}>()

const { getStatusLabel } = useScheduleLabels()

const platformOptions = [
  { value: 'YOUTUBE' as Platform, label: 'YouTube', color: '#FF0000' },
  { value: 'TIKTOK' as Platform, label: 'TikTok', color: '#000000' },
  { value: 'INSTAGRAM' as Platform, label: 'Instagram', color: '#E1306C' },
  { value: 'NAVER_CLIP' as Platform, label: 'Naver Clip', color: '#03C75A' },
]

const statusOptions = computed(() =>
  (['SCHEDULED', 'PUBLISHED', 'FAILED', 'CANCELLED'] as ScheduleStatus[]).map((value) => ({
    value,
    label: getStatusLabel(value),
    activeClass: {
      SCHEDULED: 'badge-blue',
      PUBLISHED: 'badge-success',
      FAILED: 'badge-danger',
      CANCELLED: 'badge-gray',
    }[value],
  })),
)

const activeCount = computed(() => (props.platform ? 1 : 0) + (props.status ? 1 : 0))
</script>
