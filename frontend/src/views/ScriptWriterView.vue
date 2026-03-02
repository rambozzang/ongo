<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  PlusIcon,
  DocumentTextIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
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
    <PageHeader title="AI 스크립트 작성기" description="AI를 활용하여 영상 스크립트를 자동으로 생성하고 편집하세요">
      <template #title-suffix>
        <SparklesIcon class="w-6 h-6 text-primary-600 dark:text-primary-400" />
      </template>
      <template #actions>
        <button
          @click="openGenerateModal"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          스크립트 생성
        </button>
      </template>
    </PageHeader>

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
    <BaseModal v-model="showDetailModal" :title="selectedScript?.title ?? ''" max-width="lg">
      <template v-if="selectedScript">
        <div class="mb-4 flex items-center gap-3 text-sm text-gray-500 dark:text-gray-400">
          <span>{{ formatDuration(selectedScript.targetDuration) }}</span>
          <span class="text-gray-300 dark:text-gray-600">|</span>
          <span>{{ selectedScript.estimatedWordCount.toLocaleString() }}자</span>
          <span class="text-gray-300 dark:text-gray-600">|</span>
          <span>{{ selectedScript.targetAudience || '대상 미지정' }}</span>
        </div>
        <ScriptEditor :script="selectedScript" />
      </template>
    </BaseModal>
  </div>
</template>
