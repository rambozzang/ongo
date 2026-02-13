<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  FunnelIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  ChevronDownIcon,
} from '@heroicons/vue/24/outline'
import type {
  ActivityAction,
  ActivityDateRange,
  ActivityDateCustomRange,
} from '@/types/activitylog'
import { ACTION_LABELS } from '@/types/activitylog'

const props = defineProps<{
  selectedAction: ActivityAction | null
  selectedDateRange: ActivityDateRange | null
  selectedUserId: string | null
  searchQuery: string
  users: { id: string; name: string }[]
  customDateRange: ActivityDateCustomRange
}>()

const emit = defineEmits<{
  'update:action': [action: ActivityAction | null]
  'update:dateRange': [range: ActivityDateRange | null, custom?: ActivityDateCustomRange]
  'update:userId': [userId: string | null]
  'update:searchQuery': [query: string]
  reset: []
}>()

// Local state for dropdowns
const showActionDropdown = ref(false)
const showDateDropdown = ref(false)
const showUserDropdown = ref(false)
const localSearch = ref(props.searchQuery)

// Custom date range inputs
const customStart = ref(props.customDateRange.startDate)
const customEnd = ref(props.customDateRange.endDate)

watch(
  () => props.searchQuery,
  (val) => {
    localSearch.value = val
  },
)

// Action filter options
const actionOptions: { value: ActivityAction | null; label: string }[] = [
  { value: null, label: '전체' },
  { value: 'upload', label: ACTION_LABELS.upload },
  { value: 'publish', label: ACTION_LABELS.publish },
  { value: 'edit', label: ACTION_LABELS.edit },
  { value: 'delete', label: ACTION_LABELS.delete },
  { value: 'schedule', label: ACTION_LABELS.schedule },
  { value: 'ai_generate', label: ACTION_LABELS.ai_generate },
  { value: 'channel_connect', label: ACTION_LABELS.channel_connect },
  { value: 'channel_disconnect', label: ACTION_LABELS.channel_disconnect },
  { value: 'settings_change', label: ACTION_LABELS.settings_change },
  { value: 'team_invite', label: ACTION_LABELS.team_invite },
  { value: 'team_remove', label: ACTION_LABELS.team_remove },
  { value: 'login', label: ACTION_LABELS.login },
  { value: 'credit_purchase', label: ACTION_LABELS.credit_purchase },
]

// Date range options
const dateOptions: { value: ActivityDateRange | null; label: string }[] = [
  { value: null, label: '전체 기간' },
  { value: 'today', label: '오늘' },
  { value: 'yesterday', label: '어제' },
  { value: 'this_week', label: '이번 주' },
  { value: 'this_month', label: '이번 달' },
  { value: 'custom', label: '직접 선택' },
]

// Labels for display
const selectedActionLabel = computed(() => {
  if (!props.selectedAction) return '활동 유형'
  return ACTION_LABELS[props.selectedAction]
})

const selectedDateLabel = computed(() => {
  if (!props.selectedDateRange) return '기간'
  const option = dateOptions.find((o) => o.value === props.selectedDateRange)
  return option?.label ?? '기간'
})

const selectedUserLabel = computed(() => {
  if (!props.selectedUserId) return '사용자'
  const user = props.users.find((u) => u.id === props.selectedUserId)
  return user?.name ?? '사용자'
})

const hasActiveFilters = computed(() => {
  return (
    props.selectedAction !== null ||
    props.selectedDateRange !== null ||
    props.selectedUserId !== null ||
    (props.searchQuery && props.searchQuery.trim() !== '')
  )
})

function selectAction(action: ActivityAction | null) {
  emit('update:action', action)
  showActionDropdown.value = false
}

function selectDateRange(range: ActivityDateRange | null) {
  if (range === 'custom') {
    emit('update:dateRange', range, {
      startDate: customStart.value,
      endDate: customEnd.value,
    })
  } else {
    emit('update:dateRange', range)
  }
  if (range !== 'custom') {
    showDateDropdown.value = false
  }
}

function applyCustomDate() {
  emit('update:dateRange', 'custom', {
    startDate: customStart.value,
    endDate: customEnd.value,
  })
  showDateDropdown.value = false
}

function selectUser(userId: string | null) {
  emit('update:userId', userId)
  showUserDropdown.value = false
}

function handleSearchInput() {
  emit('update:searchQuery', localSearch.value)
}

function handleReset() {
  localSearch.value = ''
  customStart.value = ''
  customEnd.value = ''
  emit('reset')
}

// Close dropdowns when clicking outside
function closeDropdowns() {
  showActionDropdown.value = false
  showDateDropdown.value = false
  showUserDropdown.value = false
}
</script>

<template>
  <div class="space-y-3" @click.self="closeDropdowns">
    <!-- Search bar -->
    <div class="relative">
      <MagnifyingGlassIcon
        class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-gray-500"
      />
      <input
        v-model="localSearch"
        type="text"
        placeholder="영상 이름, 사용자로 검색..."
        class="w-full rounded-lg border border-gray-300 bg-white py-2 pl-10 pr-4 text-sm text-gray-900 placeholder-gray-400 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500 dark:focus:border-primary-400 dark:focus:ring-primary-400"
        @input="handleSearchInput"
      />
      <button
        v-if="localSearch"
        class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
        @click="localSearch = ''; handleSearchInput()"
      >
        <XMarkIcon class="h-4 w-4" />
      </button>
    </div>

    <!-- Filter row -->
    <div class="flex flex-wrap items-center gap-2">
      <FunnelIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />

      <!-- Action type dropdown -->
      <div class="relative">
        <button
          class="inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            selectedAction
              ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-900/20 dark:text-primary-400'
              : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
          "
          @click.stop="showActionDropdown = !showActionDropdown; showDateDropdown = false; showUserDropdown = false"
        >
          {{ selectedActionLabel }}
          <ChevronDownIcon class="h-3.5 w-3.5" />
        </button>

        <Transition
          enter-active-class="transition ease-out duration-100"
          enter-from-class="transform opacity-0 scale-95"
          enter-to-class="transform opacity-100 scale-100"
          leave-active-class="transition ease-in duration-75"
          leave-from-class="transform opacity-100 scale-100"
          leave-to-class="transform opacity-0 scale-95"
        >
          <div
            v-if="showActionDropdown"
            class="absolute left-0 top-full z-20 mt-1 max-h-64 w-48 overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
          >
            <button
              v-for="option in actionOptions"
              :key="option.label"
              class="flex w-full items-center px-3 py-2 text-left text-sm transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
              :class="
                (option.value === null && selectedAction === null) || option.value === selectedAction
                  ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-700 dark:text-gray-300'
              "
              @click.stop="selectAction(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </Transition>
      </div>

      <!-- Date range dropdown -->
      <div class="relative">
        <button
          class="inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            selectedDateRange
              ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-900/20 dark:text-primary-400'
              : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
          "
          @click.stop="showDateDropdown = !showDateDropdown; showActionDropdown = false; showUserDropdown = false"
        >
          {{ selectedDateLabel }}
          <ChevronDownIcon class="h-3.5 w-3.5" />
        </button>

        <Transition
          enter-active-class="transition ease-out duration-100"
          enter-from-class="transform opacity-0 scale-95"
          enter-to-class="transform opacity-100 scale-100"
          leave-active-class="transition ease-in duration-75"
          leave-from-class="transform opacity-100 scale-100"
          leave-to-class="transform opacity-0 scale-95"
        >
          <div
            v-if="showDateDropdown"
            class="absolute left-0 top-full z-20 mt-1 w-56 rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
          >
            <button
              v-for="option in dateOptions"
              :key="option.label"
              class="flex w-full items-center px-3 py-2 text-left text-sm transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
              :class="
                (option.value === null && selectedDateRange === null) || option.value === selectedDateRange
                  ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-700 dark:text-gray-300'
              "
              @click.stop="selectDateRange(option.value)"
            >
              {{ option.label }}
            </button>

            <!-- Custom date range inputs -->
            <div
              v-if="selectedDateRange === 'custom'"
              class="border-t border-gray-200 px-3 py-3 dark:border-gray-700"
            >
              <div class="space-y-2">
                <div>
                  <label class="block text-xs font-medium text-gray-600 dark:text-gray-400">시작일</label>
                  <input
                    v-model="customStart"
                    type="date"
                    class="mt-1 w-full rounded border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
                  />
                </div>
                <div>
                  <label class="block text-xs font-medium text-gray-600 dark:text-gray-400">종료일</label>
                  <input
                    v-model="customEnd"
                    type="date"
                    class="mt-1 w-full rounded border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
                  />
                </div>
                <button
                  class="w-full rounded-md bg-primary-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-700"
                  @click.stop="applyCustomDate"
                >
                  적용
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <!-- User filter dropdown (for team accounts) -->
      <div v-if="users.length > 1" class="relative">
        <button
          class="inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            selectedUserId
              ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-900/20 dark:text-primary-400'
              : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
          "
          @click.stop="showUserDropdown = !showUserDropdown; showActionDropdown = false; showDateDropdown = false"
        >
          {{ selectedUserLabel }}
          <ChevronDownIcon class="h-3.5 w-3.5" />
        </button>

        <Transition
          enter-active-class="transition ease-out duration-100"
          enter-from-class="transform opacity-0 scale-95"
          enter-to-class="transform opacity-100 scale-100"
          leave-active-class="transition ease-in duration-75"
          leave-from-class="transform opacity-100 scale-100"
          leave-to-class="transform opacity-0 scale-95"
        >
          <div
            v-if="showUserDropdown"
            class="absolute left-0 top-full z-20 mt-1 w-44 rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
          >
            <button
              class="flex w-full items-center px-3 py-2 text-left text-sm transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
              :class="
                selectedUserId === null
                  ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-700 dark:text-gray-300'
              "
              @click.stop="selectUser(null)"
            >
              전체 사용자
            </button>
            <button
              v-for="user in users"
              :key="user.id"
              class="flex w-full items-center px-3 py-2 text-left text-sm transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
              :class="
                selectedUserId === user.id
                  ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-700 dark:text-gray-300'
              "
              @click.stop="selectUser(user.id)"
            >
              {{ user.name }}
            </button>
          </div>
        </Transition>
      </div>

      <!-- Reset filters button -->
      <button
        v-if="hasActiveFilters"
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
        @click="handleReset"
      >
        <XMarkIcon class="h-3.5 w-3.5" />
        필터 초기화
      </button>
    </div>
  </div>
</template>
