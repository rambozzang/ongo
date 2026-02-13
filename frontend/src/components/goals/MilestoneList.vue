<script setup lang="ts">
import { CheckCircleIcon, CircleStackIcon } from '@heroicons/vue/24/outline'
import { CheckCircleIcon as CheckCircleSolid } from '@heroicons/vue/24/solid'
import type { Milestone } from '@/types/goal'
import { ref } from 'vue'

interface Props {
  milestones: Milestone[]
  currentValue: number
  unit: string
}

interface Emits {
  (e: 'complete', milestoneId: number): void
  (e: 'add', milestone: Omit<Milestone, 'id' | 'isCompleted' | 'completedAt'>): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const showAddForm = ref(false)
const newMilestoneTitle = ref('')
const newMilestoneTarget = ref<number | ''>('')

const celebratingId = ref<number | null>(null)

const handleComplete = (milestone: Milestone) => {
  if (!milestone.isCompleted) {
    celebratingId.value = milestone.id
    emit('complete', milestone.id)
    setTimeout(() => {
      celebratingId.value = null
    }, 2000)
  }
}

const handleAddMilestone = () => {
  if (newMilestoneTitle.value.trim() && newMilestoneTarget.value !== '') {
    emit('add', {
      title: newMilestoneTitle.value.trim(),
      targetValue: Number(newMilestoneTarget.value),
    })
    newMilestoneTitle.value = ''
    newMilestoneTarget.value = ''
    showAddForm.value = false
  }
}

const formatNumber = (value: number): string => {
  return value.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="space-y-4">
    <div class="relative">
      <!-- Connecting line -->
      <div
        v-if="milestones.length > 1"
        class="absolute left-4 top-8 bottom-8 w-0.5 bg-gradient-to-b from-gray-300 via-gray-300 to-gray-300 dark:from-gray-600 dark:via-gray-600 dark:to-gray-600"
      ></div>

      <!-- Milestone items -->
      <div class="space-y-4">
        <div
          v-for="milestone in milestones"
          :key="milestone.id"
          class="relative flex items-start gap-3 group"
        >
          <!-- Checkpoint -->
          <button
            @click="handleComplete(milestone)"
            class="relative z-10 flex-shrink-0 transition-transform hover:scale-110 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 rounded-full"
            :disabled="milestone.isCompleted"
          >
            <CheckCircleSolid
              v-if="milestone.isCompleted"
              class="w-8 h-8 text-green-500 dark:text-green-400"
            />
            <CheckCircleIcon
              v-else-if="currentValue >= milestone.targetValue"
              class="w-8 h-8 text-blue-500 dark:text-blue-400 animate-pulse"
            />
            <CircleStackIcon
              v-else
              class="w-8 h-8 text-gray-400 dark:text-gray-500"
            />
          </button>

          <!-- Content -->
          <div
            class="flex-1 min-w-0 pt-1"
            :class="{
              'opacity-50': !milestone.isCompleted && currentValue < milestone.targetValue,
            }"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="flex-1 min-w-0">
                <p
                  class="text-sm font-medium"
                  :class="{
                    'text-green-600 dark:text-green-400 line-through': milestone.isCompleted,
                    'text-gray-900 dark:text-gray-100': !milestone.isCompleted && currentValue >= milestone.targetValue,
                    'text-gray-600 dark:text-gray-400': !milestone.isCompleted && currentValue < milestone.targetValue,
                  }"
                >
                  {{ milestone.title }}
                </p>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                  목표: {{ formatNumber(milestone.targetValue) }}{{ unit }}
                </p>
                <p
                  v-if="milestone.isCompleted && milestone.completedAt"
                  class="text-xs text-green-600 dark:text-green-400 mt-0.5"
                >
                  달성: {{ new Date(milestone.completedAt).toLocaleDateString('ko-KR') }}
                </p>
              </div>

              <!-- Badge -->
              <span
                v-if="milestone.isCompleted"
                class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300"
              >
                완료
              </span>
              <span
                v-else-if="currentValue >= milestone.targetValue"
                class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300 animate-pulse"
              >
                달성!
              </span>
            </div>
          </div>

          <!-- Celebration animation -->
          <div
            v-if="celebratingId === milestone.id"
            class="absolute inset-0 pointer-events-none"
          >
            <div class="confetti-container">
              <div class="confetti" v-for="i in 12" :key="i"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Add milestone button/form -->
    <div class="pt-2">
      <button
        v-if="!showAddForm"
        @click="showAddForm = true"
        class="w-full px-4 py-2 text-sm text-gray-600 dark:text-gray-400 border border-dashed border-gray-300 dark:border-gray-600 rounded-lg hover:border-blue-500 dark:hover:border-blue-400 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
      >
        + 마일스톤 추가
      </button>

      <div
        v-else
        class="p-4 border border-gray-300 dark:border-gray-600 rounded-lg bg-gray-50 dark:bg-gray-800/50"
      >
        <div class="space-y-3">
          <div>
            <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              마일스톤 제목
            </label>
            <input
              v-model="newMilestoneTitle"
              type="text"
              placeholder="예: 첫 번째 목표 달성"
              class="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
            />
          </div>

          <div>
            <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              목표 값
            </label>
            <div class="flex items-center gap-2">
              <input
                v-model.number="newMilestoneTarget"
                type="number"
                placeholder="0"
                class="flex-1 px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
              />
              <span class="text-sm text-gray-600 dark:text-gray-400">{{ unit }}</span>
            </div>
          </div>

          <div class="flex gap-2">
            <button
              @click="handleAddMilestone"
              :disabled="!newMilestoneTitle.trim() || newMilestoneTarget === ''"
              class="flex-1 px-3 py-1.5 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              추가
            </button>
            <button
              @click="showAddForm = false"
              class="flex-1 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 rounded-lg transition-colors"
            >
              취소
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes confetti-fall {
  0% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translateY(200px) rotate(360deg);
    opacity: 0;
  }
}

.confetti-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}

.confetti {
  position: absolute;
  width: 8px;
  height: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  animation: confetti-fall 2s ease-out forwards;
  top: 50%;
  left: 50%;
}

.confetti:nth-child(1) { left: 10%; animation-delay: 0s; background: #fbbf24; }
.confetti:nth-child(2) { left: 20%; animation-delay: 0.1s; background: #ef4444; }
.confetti:nth-child(3) { left: 30%; animation-delay: 0.2s; background: #3b82f6; }
.confetti:nth-child(4) { left: 40%; animation-delay: 0.3s; background: #10b981; }
.confetti:nth-child(5) { left: 50%; animation-delay: 0.4s; background: #8b5cf6; }
.confetti:nth-child(6) { left: 60%; animation-delay: 0.5s; background: #f59e0b; }
.confetti:nth-child(7) { left: 70%; animation-delay: 0.1s; background: #ec4899; }
.confetti:nth-child(8) { left: 80%; animation-delay: 0.2s; background: #14b8a6; }
.confetti:nth-child(9) { left: 90%; animation-delay: 0.3s; background: #f97316; }
.confetti:nth-child(10) { left: 15%; animation-delay: 0.4s; background: #06b6d4; }
.confetti:nth-child(11) { left: 65%; animation-delay: 0s; background: #a855f7; }
.confetti:nth-child(12) { left: 85%; animation-delay: 0.1s; background: #22c55e; }
</style>
