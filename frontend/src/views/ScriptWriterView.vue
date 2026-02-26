<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  PlusIcon,
  DocumentTextIcon,
  SparklesIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ScriptCard from '@/components/scriptwriter/ScriptCard.vue'
import ScriptEditor from '@/components/scriptwriter/ScriptEditor.vue'
import GenerateModal from '@/components/scriptwriter/GenerateModal.vue'
import { useScriptWriterStore } from '@/stores/scriptWriter'
import type { Script, ScriptStatus, GenerateScriptRequest } from '@/types/scriptWriter'

const store = useScriptWriterStore()
const { scripts, summary, isLoading, isGenerating } = storeToRefs(store)

const showGenerateModal = ref(false)
const showDetailModal = ref(false)
const selectedScript = ref<Script | null>(null)
const statusFilter = ref<ScriptStatus | ''>('')

const filteredScripts = computed(() => {
  if (!statusFilter.value) return scripts.value
  return scripts.value.filter((s) => s.status === statusFilter.value)
})

const statusOptions: { value: ScriptStatus | ''; label: string }[] = [
  { value: '', label: '전체' },
  { value: 'DRAFT', label: '초안' },
  { value: 'REVIEWING', label: '검토중' },
  { value: 'FINAL', label: '완성' },
  { value: 'ARCHIVED', label: '보관' },
]

onMounted(() => {
  store.fetchScripts()
  store.fetchSummary()
})

function openGenerateModal() {
  showGenerateModal.value = true
}

async function handleGenerate(request: GenerateScriptRequest) {
  const created = await store.generateScript(request)
  showGenerateModal.value = false
  if (created) {
    selectedScript.value = created
    showDetailModal.value = true
  }
}

function handleCardClick(id: number) {
  const script = scripts.value.find((s) => s.id === id)
  if (script) {
    selectedScript.value = script
    showDetailModal.value = true
  }
}

function handleDelete(id: number) {
  if (confirm('이 스크립트를 삭제하시겠습니까?')) {
    store.deleteScript(id)
    if (selectedScript.value?.id === id) {
      showDetailModal.value = false
      selectedScript.value = null
    }
  }
}

function formatDuration(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return sec > 0 ? `${min}분 ${sec}초` : `${min}분`
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            AI 스크립트 작성기
          </h1>
          <SparklesIcon class="w-6 h-6 text-primary-600 dark:text-primary-400" />
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          AI를 활용하여 영상 스크립트를 자동으로 생성하고 편집하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="openGenerateModal"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          스크립트 생성
        </button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div
      v-if="summary"
      class="grid grid-cols-2 tablet:grid-cols-4 gap-4 mb-6"
    >
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <p class="text-sm text-gray-500 dark:text-gray-400">전체 스크립트</p>
        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1">{{ summary.totalScripts }}</p>
      </div>
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <p class="text-sm text-gray-500 dark:text-gray-400">초안</p>
        <p class="text-2xl font-bold text-yellow-600 dark:text-yellow-400 mt-1">{{ summary.drafts }}</p>
      </div>
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <p class="text-sm text-gray-500 dark:text-gray-400">완성</p>
        <p class="text-2xl font-bold text-green-600 dark:text-green-400 mt-1">{{ summary.finals }}</p>
      </div>
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <p class="text-sm text-gray-500 dark:text-gray-400">사용 크레딧</p>
        <p class="text-2xl font-bold text-primary-600 dark:text-primary-400 mt-1">{{ summary.totalCreditsUsed }}</p>
      </div>
    </div>

    <!-- Filter -->
    <div class="mb-6 flex items-center gap-2">
      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">상태:</span>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          @click="statusFilter = opt.value"
          :class="[
            'px-3 py-1.5 rounded-full text-sm font-medium transition-colors',
            statusFilter === opt.value
              ? 'bg-primary-600 text-white dark:bg-primary-500'
              : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700',
          ]"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Script Grid -->
    <div v-else-if="filteredScripts.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
      <ScriptCard
        v-for="script in filteredScripts"
        :key="script.id"
        :script="script"
        @click="handleCardClick"
        @delete="handleDelete"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
    >
      <DocumentTextIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
      <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        스크립트가 없습니다
      </h3>
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
        AI를 활용하여 첫 번째 영상 스크립트를 만들어 보세요
      </p>
      <button @click="openGenerateModal" class="btn-primary inline-flex items-center gap-2">
        <SparklesIcon class="w-5 h-5" />
        스크립트 생성하기
      </button>
    </div>

    <!-- Generate Modal -->
    <GenerateModal
      :visible="showGenerateModal"
      :is-generating="isGenerating"
      @close="showGenerateModal = false"
      @generate="handleGenerate"
    />

    <!-- Detail / Editor Modal -->
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
          v-if="showDetailModal && selectedScript"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showDetailModal = false"
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
              v-if="showDetailModal && selectedScript"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Detail Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <div class="flex-1 min-w-0">
                  <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 truncate">
                    {{ selectedScript.title }}
                  </h2>
                  <div class="flex items-center gap-3 mt-1 text-sm text-gray-500 dark:text-gray-400">
                    <span>{{ formatDuration(selectedScript.targetDuration) }}</span>
                    <span class="text-gray-300 dark:text-gray-600">|</span>
                    <span>{{ selectedScript.estimatedWordCount.toLocaleString() }}자</span>
                    <span class="text-gray-300 dark:text-gray-600">|</span>
                    <span>{{ selectedScript.targetAudience || '대상 미지정' }}</span>
                  </div>
                </div>
                <button
                  @click="showDetailModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ml-4"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Detail Body -->
              <div class="flex-1 overflow-y-auto px-6 py-6">
                <ScriptEditor :script="selectedScript" />
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
