<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  FunnelIcon,
  CheckCircleIcon,
  ClockIcon,
  ChartBarIcon,
  CalendarIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import { useGoalsStore } from '@/stores/goals'
import type { Goal } from '@/types/goal'
import GoalCard from '@/components/goals/GoalCard.vue'
import GoalFormModal from '@/components/goals/GoalFormModal.vue'
import MilestoneList from '@/components/goals/MilestoneList.vue'

const { t } = useI18n()
const goalsStore = useGoalsStore()

const showFormModal = ref(false)
const editingGoal = ref<Goal | undefined>(undefined)
const viewingGoal = ref<Goal | undefined>(undefined)
const showCompleted = ref(false)
const sortBy = ref<'deadline' | 'progress' | 'recent'>('deadline')

onMounted(() => {
  goalsStore.loadFromLocalStorage()
})

const displayedGoals = computed(() => {
  let goals = showCompleted.value ? goalsStore.completedGoals : goalsStore.activeGoals

  // Sort
  const sorted = [...goals].sort((a, b) => {
    if (sortBy.value === 'deadline') {
      return new Date(a.endDate).getTime() - new Date(b.endDate).getTime()
    } else if (sortBy.value === 'progress') {
      const aProgress = (a.currentValue / a.targetValue) * 100
      const bProgress = (b.currentValue / b.targetValue) * 100
      return bProgress - aProgress
    } else {
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    }
  })

  return sorted
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
  if (confirm(t('goals.confirmDelete'))) {
    goalsStore.deleteGoal(goalId)
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
    goalsStore.saveToLocalStorage()
  }
}

const formatNumber = (value: number): string => {
  return value.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('goals.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('goals.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="handleCreateGoal"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          {{ $t('goals.newGoal') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('goals.pageGuideTitle')" :items="($tm('goals.pageGuide') as string[])" />

    <div class="mt-6">

      <!-- Stats Overview -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.activeGoals') }}</p>
              <p class="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ stats.active }}
              </p>
            </div>
            <div class="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
              <ChartBarIcon class="w-6 h-6 text-blue-600 dark:text-blue-400" />
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.completed') }}</p>
              <p class="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ stats.completed }}
              </p>
            </div>
            <div class="p-3 bg-green-100 dark:bg-green-900/30 rounded-lg">
              <CheckCircleIcon class="w-6 h-6 text-green-600 dark:text-green-400" />
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.overallProgress') }}</p>
              <p class="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ stats.progress }}%
              </p>
            </div>
            <div class="p-3 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
              <ClockIcon class="w-6 h-6 text-purple-600 dark:text-purple-400" />
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-gray-600 dark:text-gray-400">{{ $t('goals.nextDeadline') }}</p>
              <p class="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                {{ nextDeadline ? `D-${nextDeadline.daysLeft}` : '-' }}
              </p>
            </div>
            <div class="p-3 bg-orange-100 dark:bg-orange-900/30 rounded-lg">
              <CalendarIcon class="w-6 h-6 text-orange-600 dark:text-orange-400" />
            </div>
          </div>
        </div>
      </div>

      <!-- Upcoming Deadlines Alert -->
      <div
        v-if="stats.upcoming > 0"
        class="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-lg p-4 mb-6"
      >
        <div class="flex items-start gap-3">
          <CalendarIcon class="w-5 h-5 text-orange-600 dark:text-orange-400 mt-0.5" />
          <div class="flex-1">
            <h3 class="text-sm font-medium text-orange-900 dark:text-orange-200">
              {{ $t('goals.upcomingDeadline') }}
            </h3>
            <p class="text-sm text-orange-700 dark:text-orange-300 mt-1">
              {{ $t('goals.upcomingDeadlineDesc', { count: stats.upcoming }) }}
            </p>
          </div>
        </div>
      </div>

      <!-- Filters -->
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-2">
          <button
            @click="showCompleted = false"
            :class="[
              'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
              !showCompleted
                ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
            ]"
          >
            {{ $t('goals.activeGoalsFilter', { count: stats.active }) }}
          </button>
          <button
            @click="showCompleted = true"
            :class="[
              'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
              showCompleted
                ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
            ]"
          >
            {{ $t('goals.completedGoalsFilter', { count: stats.completed }) }}
          </button>
        </div>

        <div class="flex items-center gap-2">
          <FunnelIcon class="w-5 h-5 text-gray-400" />
          <select
            v-model="sortBy"
            class="px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
          >
            <option value="deadline">{{ $t('goals.sortDeadline') }}</option>
            <option value="progress">{{ $t('goals.sortProgress') }}</option>
            <option value="recent">{{ $t('goals.sortRecent') }}</option>
          </select>
        </div>
      </div>

      <!-- Goals Grid -->
      <div v-if="displayedGoals.length > 0" class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <GoalCard
          v-for="goal in displayedGoals"
          :key="goal.id"
          :goal="goal"
          @edit="handleEditGoal"
          @delete="handleDeleteGoal"
          @pause="handlePauseGoal"
          @resume="handleResumeGoal"
          @view-details="handleViewDetails"
        />
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-12 text-center"
      >
        <ChartBarIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          {{ showCompleted ? $t('goals.emptyCompletedTitle') : $t('goals.emptyActiveTitle') }}
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
          {{ showCompleted ? $t('goals.emptyCompletedDesc') : $t('goals.emptyActiveDesc') }}
        </p>
        <button
          v-if="!showCompleted"
          @click="handleCreateGoal"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          {{ $t('goals.createFirstGoal') }}
        </button>
      </div>
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
                    <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
                      {{ viewingGoal.title }}
                    </h2>
                    <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      {{ viewingGoal.description }}
                    </p>
                  </div>
                  <button
                    @click="handleCloseDetails"
                    class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ml-4"
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
                      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('goals.progressStatus') }}</span>
                      <span class="text-2xl font-bold text-gray-900 dark:text-gray-100">
                        {{ Math.round((viewingGoal.currentValue / viewingGoal.targetValue) * 100) }}%
                      </span>
                    </div>
                    <div class="w-full h-3 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        class="h-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400 transition-all duration-500 rounded-full"
                        :style="{ width: `${Math.min((viewingGoal.currentValue / viewingGoal.targetValue) * 100, 100)}%` }"
                      ></div>
                    </div>
                    <div class="flex items-center justify-between mt-2 text-sm text-gray-600 dark:text-gray-400">
                      <span>{{ $t('goals.current') }}: {{ formatNumber(viewingGoal.currentValue) }} {{ viewingGoal.unit }}</span>
                      <span>{{ $t('goals.target') }}: {{ formatNumber(viewingGoal.targetValue) }} {{ viewingGoal.unit }}</span>
                    </div>
                  </div>

                  <!-- Milestones -->
                  <div>
                    <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
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
  </div>
</template>
