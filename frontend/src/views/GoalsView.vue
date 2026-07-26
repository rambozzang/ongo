<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  TrashIcon,
  CheckCircleIcon,
  ClockIcon,
  ChartBarIcon,
  CalendarIcon,
  MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'
import AsyncState from '@/components/common/AsyncState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useGoalsStore } from '@/stores/goals'
import { useNotificationStore } from '@/stores/notification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import type { Goal } from '@/types/goal'
import GoalCard from '@/components/goals/GoalCard.vue'
import GoalFormModal from '@/components/goals/GoalFormModal.vue'
import MilestoneList from '@/components/goals/MilestoneList.vue'

const { t } = useI18n({ useScope: 'global' })
const goalsStore = useGoalsStore()
const notificationStore = useNotificationStore()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const showFormModal = ref(false)
const editingGoal = ref<Goal | undefined>(undefined)
const viewingGoal = ref<Goal | undefined>(undefined)
const showCompleted = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

onMounted(() => {
  goalsStore.fetchGoals()
})

const progressRatio = (goal: Goal): number =>
  goal.targetValue > 0 ? goal.currentValue / goal.targetValue : 0

const sortOptions = computed<ListSortOption<Goal>[]>(() => [
  { key: 'deadline', label: t('goals.sortDeadline'), accessor: 'endDate', kind: 'date', defaultDir: 'asc' },
  { key: 'progress', label: t('goals.sortProgress'), accessor: progressRatio, kind: 'number', defaultDir: 'desc' },
  { key: 'recent', label: t('goals.sortRecent'), accessor: 'createdAt', kind: 'date', defaultDir: 'desc' },
  { key: 'title', label: t('goals.sortTitle'), accessor: 'title', kind: 'string', defaultDir: 'asc' },
])

const {
  query,
  hasQuery,
  sortKey,
  sortDir,
  filtered,
  visibleCount,
  isSourceEmpty,
  isResultEmpty,
  resetFilters,
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  toggleAll,
  clearSelection,
} = useListControls<Goal>(() => goalsStore.goals, {
  searchFields: ['title', 'description'],
  sortOptions,
  defaultSortKey: 'deadline',
  // 활성/달성 탭은 필터로 처리한다 — 원본이 비었는지(데이터 없음)와
  // 탭 결과만 비었는지를 구분하려면 원본 배열을 그대로 넘겨야 한다.
  filters: computed(() => [
    (goal: Goal) => (showCompleted.value ? goal.status === 'completed' : goal.status === 'active'),
  ]),
})

const stats = computed(() => {
  const active = goalsStore.activeGoals.length
  const completed = goalsStore.completedGoals.length
  const progress = goalsStore.overallProgress
  const upcoming = goalsStore.upcomingDeadlines.length

  return { active, completed, progress, upcoming }
})

const nextDeadline = computed(() => {
  const upcoming = goalsStore.upcomingDeadlines
  if (upcoming.length === 0) return null

  const goal = upcoming[0]
  const now = new Date()
  const end = new Date(goal.endDate)
  const daysLeft = Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))

  return {
    goal,
    daysLeft,
  }
})

const handleCreateGoal = () => {
  editingGoal.value = undefined
  showFormModal.value = true
}

const handleEditGoal = (goal: Goal) => {
  editingGoal.value = goal
  showFormModal.value = true
}

const handleDeleteGoal = (goalId: number) => {
  deleteTargetId.value = goalId
  showDeleteModal.value = true
}

const confirmDeleteGoal = async () => {
  const goalId = deleteTargetId.value
  deleteTargetId.value = null
  if (goalId === null) return
  try {
    await goalsStore.deleteGoal(goalId)
  } catch {
    notificationStore.error(t('goals.deleteFailed'))
  }
}

const handleBulkDelete = async () => {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map(id => goalsStore.deleteGoal(id)))
    notificationStore.success(t('goals.bulkDeleteDone', { count: ids.length }))
  } catch {
    notificationStore.error(t('goals.deleteFailed'))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

const handlePauseGoal = (goalId: number) => {
  goalsStore.pauseGoal(goalId)
}

const handleResumeGoal = (goalId: number) => {
  goalsStore.resumeGoal(goalId)
}

const handleViewDetails = (goal: Goal) => {
  viewingGoal.value = goal
}

const handleCloseDetails = () => {
  viewingGoal.value = undefined
}

const handleFormSubmit = (goal: Omit<Goal, 'id' | 'createdAt' | 'completedAt'>) => {
  if (editingGoal.value) {
    goalsStore.updateGoal(editingGoal.value.id, goal)
  } else {
    goalsStore.createGoal(goal)
  }
}

const handleCompleteMilestone = (goalId: number, milestoneId: number) => {
  goalsStore.completeMilestone(goalId, milestoneId)
}

const handleAddMilestone = (goalId: number, milestone: { title: string; targetValue: number }) => {
  const goal = goalsStore.goals.find(g => g.id === goalId)
  if (goal) {
    const newMilestone = {
      id: Math.max(...goal.milestones.map(m => m.id), 0) + 1,
      ...milestone,
      isCompleted: false,
      completedAt: null,
    }
    goal.milestones.push(newMilestone)
  }
}

const formatNumber = (value: number): string => {
  return value.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('goals.title')" :description="$t('goals.description')">
      <template #actions>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="handleCreateGoal"
        >
          <PlusIcon class="w-5 h-5" />
          {{ $t('goals.newGoal') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('goals.pageGuideTitle')" :items="($tm('goals.pageGuide') as string[])" />

    <div class="mt-6">

      <!-- Stats Overview -->
      <div class="page-grid page-grid--metrics mb-8">
        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-body font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.activeGoals') }}</p>
              <p class="text-display font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ stats.active }}
              </p>
            </div>
            <div class="p-3 bg-info-subtle rounded-lg">
              <ChartBarIcon class="w-6 h-6 text-info-strong" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-body font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.completed') }}</p>
              <p class="text-display font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ stats.completed }}
              </p>
            </div>
            <div class="p-3 bg-success-subtle rounded-lg">
              <CheckCircleIcon class="w-6 h-6 text-success-strong" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-body font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.overallProgress') }}</p>
              <p class="text-display font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ stats.progress }}%
              </p>
            </div>
            <div class="p-3 bg-info-subtle rounded-lg">
              <ClockIcon class="w-6 h-6 text-info-strong" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-body font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.nextDeadline') }}</p>
              <p class="text-display font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ nextDeadline ? `D-${nextDeadline.daysLeft}` : '-' }}
              </p>
            </div>
            <div class="p-3 bg-warning-subtle rounded-lg">
              <CalendarIcon class="w-6 h-6 text-warning-strong" />
            </div>
          </div>
        </div>
      </div>

      <!-- Upcoming Deadlines Alert -->
      <div
        v-if="stats.upcoming > 0"
        class="bg-warning-subtle border border-warning rounded-lg p-4 mb-6"
      >
        <div class="flex items-start gap-3">
          <CalendarIcon class="w-5 h-5 text-warning-strong mt-0.5" />
          <div class="flex-1">
            <h3 class="text-body font-medium text-warning-strong">
              {{ $t('goals.upcomingDeadline') }}
            </h3>
            <p class="text-body text-warning-strong mt-1">
              {{ $t('goals.upcomingDeadlineDesc', { count: stats.upcoming }) }}
            </p>
          </div>
        </div>
      </div>

      <!-- 검색 · 정렬 · 일괄 작업 -->
      <ListToolbar
        v-if="!isSourceEmpty"
        v-model="query"
        v-model:sort-key="sortKey"
        v-model:sort-dir="sortDir"
        :sort-options="sortOptions"
        :selected-count="selectedCount"
        :total-count="visibleCount"
        :search-placeholder="$t('goals.searchPlaceholder')"
        :search-label="$t('goals.searchLabel')"
        @clear-selection="clearSelection"
      >
        <template #filters>
          <button
            type="button"
            :class="[
              'min-h-10 px-4 py-2 text-body font-medium rounded-lg transition-colors',
              !showCompleted
                ? 'bg-info-subtle text-info-strong'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
            ]"
            @click="showCompleted = false"
          >
            {{ $t('goals.activeGoalsFilter', { count: stats.active }) }}
          </button>
          <button
            type="button"
            :class="[
              'min-h-10 px-4 py-2 text-body font-medium rounded-lg transition-colors',
              showCompleted
                ? 'bg-info-subtle text-info-strong'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
            ]"
            @click="showCompleted = true"
          >
            {{ $t('goals.completedGoalsFilter', { count: stats.completed }) }}
          </button>
        </template>

        <template #bulk-actions>
          <button
            type="button"
            class="btn-danger inline-flex items-center gap-1.5"
            :disabled="bulkDeleting"
            @click="showBulkDeleteModal = true"
          >
            <TrashIcon class="h-4 w-4" aria-hidden="true" />
            {{ $t('list.bulkDelete') }}
          </button>
        </template>
      </ListToolbar>

      <!-- 전체 선택 -->
      <div v-if="!isSourceEmpty && visibleCount > 0" class="mb-3 flex items-center gap-2">
        <input
          id="goals-select-all"
          type="checkbox"
          :class="CHECKBOX_CLASS"
          :checked="allSelected"
          :indeterminate="someSelected"
          @change="toggleAll"
        />
        <label for="goals-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
          {{ $t('list.selectAll', { count: visibleCount }) }}
        </label>
      </div>

      <AsyncState
        :loading="goalsStore.loading && isSourceEmpty"
        :empty="isSourceEmpty"
        skeleton="card"
        :skeleton-count="4"
        :empty-icon="ChartBarIcon"
        :empty-title="$t('goals.emptyActiveTitle')"
        :empty-description="$t('goals.emptyActiveDesc')"
        :empty-action-label="$t('goals.createFirstGoal')"
        :retryable="false"
        @empty-action="handleCreateGoal"
      >
        <!-- 검색 결과가 없을 때 -->
        <EmptyState
          v-if="isResultEmpty && hasQuery"
          :icon="MagnifyingGlassIcon"
          :title="$t('list.noResultsTitle')"
          :description="$t('list.noResultsDescription')"
          :action-label="$t('list.resetFilters')"
          @action="resetFilters"
        />

        <!-- 탭에 해당하는 목표가 없을 때 -->
        <EmptyState
          v-else-if="isResultEmpty"
          :icon="ChartBarIcon"
          :title="showCompleted ? $t('goals.emptyCompletedTitle') : $t('goals.emptyActiveTitle')"
          :description="showCompleted ? $t('goals.emptyCompletedDesc') : $t('goals.emptyActiveDesc')"
          :action-label="showCompleted ? undefined : $t('goals.createFirstGoal')"
          @action="handleCreateGoal"
        />

        <!-- Goals Grid -->
        <div v-else class="page-grid page-grid--split">
          <div
            v-for="goal in filtered"
            :key="goal.id"
            class="flex items-start gap-3"
          >
            <input
              type="checkbox"
              :class="[CHECKBOX_CLASS, 'mt-6']"
              :checked="isSelected(goal.id)"
              :aria-label="$t('list.selectItem', { name: goal.title })"
              @change="toggle(goal.id)"
            />
            <GoalCard
              :goal="goal"
              class="min-w-0 flex-1"
              @edit="handleEditGoal"
              @delete="handleDeleteGoal"
              @pause="handlePauseGoal"
              @resume="handleResumeGoal"
              @view-details="handleViewDetails"
            />
          </div>
        </div>
      </AsyncState>
    </div>

    <!-- Goal Form Modal -->
    <GoalFormModal
      :show="showFormModal"
      :goal="editingGoal"
      @close="showFormModal = false"
      @submit="handleFormSubmit"
    />

    <!-- Goal Details Modal -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="viewingGoal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="handleCloseDetails"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="viewingGoal"
              class="bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                <div class="flex items-start justify-between">
                  <div class="flex-1">
                    <h2 class="text-h2 font-semibold text-gray-900 dark:text-gray-100">
                      {{ viewingGoal.title }}
                    </h2>
                    <p class="text-body text-gray-600 dark:text-gray-400 mt-1">
                      {{ viewingGoal.description }}
                    </p>
                  </div>
                  <button
                    class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ml-4"
                    @click="handleCloseDetails"
                  >
                    <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>

              <!-- Body -->
              <div class="flex-1 overflow-y-auto px-6 py-6">
                <div class="space-y-6">
                  <!-- Progress Info -->
                  <div class="bg-gray-50 dark:bg-gray-900 rounded-lg p-4">
                    <div class="flex items-center justify-between mb-2">
                      <span class="text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('goals.progressStatus') }}</span>
                      <span class="text-h1 font-bold text-gray-900 dark:text-gray-100">
                        {{ Math.round(progressRatio(viewingGoal) * 100) }}%
                      </span>
                    </div>
                    <div class="w-full h-3 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        class="h-full bg-primary-600 transition-all duration-500 rounded-full"
                        :style="{ width: `${Math.min(progressRatio(viewingGoal) * 100, 100)}%` }"
                      ></div>
                    </div>
                    <div class="flex items-center justify-between mt-2 text-body text-gray-600 dark:text-gray-400">
                      <span>{{ $t('goals.current') }}: {{ formatNumber(viewingGoal.currentValue) }} {{ viewingGoal.unit }}</span>
                      <span>{{ $t('goals.target') }}: {{ formatNumber(viewingGoal.targetValue) }} {{ viewingGoal.unit }}</span>
                    </div>
                  </div>

                  <!-- Milestones -->
                  <div>
                    <h3 class="text-title font-semibold text-gray-900 dark:text-gray-100 mb-4">
                      {{ $t('goals.milestones') }}
                    </h3>
                    <MilestoneList
                      :milestones="viewingGoal.milestones"
                      :current-value="viewingGoal.currentValue"
                      :unit="viewingGoal.unit"
                      @complete="(id) => handleCompleteMilestone(viewingGoal!.id, id)"
                      @add="(milestone) => handleAddMilestone(viewingGoal!.id, milestone)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>

    <!-- 목표 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('goals.deleteTitle')"
      :message="$t('goals.confirmDelete')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDeleteGoal"
      @cancel="deleteTargetId = null"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('goals.bulkDeleteTitle')"
      :message="$t('goals.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>
