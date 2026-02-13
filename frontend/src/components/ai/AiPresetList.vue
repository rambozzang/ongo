<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">프리셋</h2>
      <p class="text-sm text-gray-500 dark:text-gray-400">자주 사용하는 프롬프트를 저장하고 빠르게 재사용하세요</p>
    </div>

    <!-- Preset Grid -->
    <div class="grid gap-4 tablet:grid-cols-2">
      <!-- Existing Presets -->
      <div
        v-for="preset in presets"
        :key="preset.id"
        class="card group relative cursor-pointer transition-all hover:shadow-md hover:border-primary-200 dark:hover:border-primary-800"
        @click="handlePresetClick(preset)"
      >
        <!-- Edit/Delete buttons (only for non-default presets) -->
        <div
          v-if="!preset.isDefault"
          class="absolute right-3 top-3 flex gap-1 opacity-0 transition-opacity group-hover:opacity-100"
        >
          <button
            class="rounded-md p-1.5 text-gray-400 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
            @click.stop="handleEdit(preset)"
          >
            <PencilIcon class="h-4 w-4" />
          </button>
          <button
            class="rounded-md p-1.5 text-gray-400 transition-colors hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-600 dark:hover:text-red-400"
            @click.stop="handleDelete(preset)"
          >
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>

        <!-- Badge for tool type -->
        <div class="mb-3 flex items-center gap-2">
          <span class="badge-blue text-xs">{{ getToolTypeLabel(preset.toolType) }}</span>
          <span v-if="preset.isDefault" class="badge-gray text-xs">기본</span>
        </div>

        <!-- Preset Name -->
        <h3 class="mb-2 font-semibold text-gray-900 dark:text-gray-100 group-hover:text-primary-600 dark:group-hover:text-primary-400 transition-colors">
          {{ preset.name }}
        </h3>

        <!-- Description -->
        <p class="mb-3 text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
          {{ preset.description }}
        </p>

        <!-- Prompt Preview -->
        <div class="mb-4 rounded-lg bg-gray-50 dark:bg-gray-900 px-3 py-2">
          <p class="text-xs text-gray-600 dark:text-gray-300 line-clamp-2">
            {{ preset.prompt }}
          </p>
        </div>

        <!-- Use Button -->
        <button
          class="btn-primary w-full text-sm"
          @click.stop="handlePresetClick(preset)"
        >
          사용하기
        </button>
      </div>

      <!-- Add New Preset Card -->
      <div
        class="card cursor-pointer border-2 border-dashed border-gray-300 dark:border-gray-600 transition-all hover:border-primary-400 dark:hover:border-primary-500 hover:bg-primary-50/50 dark:hover:bg-primary-900/10"
        @click="handleAddNew"
      >
        <div class="flex h-full flex-col items-center justify-center py-8 text-center">
          <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-primary-100 dark:bg-primary-900/30">
            <PlusIcon class="h-6 w-6 text-primary-600 dark:text-primary-400" />
          </div>
          <h3 class="mb-1 font-medium text-gray-900 dark:text-gray-100">새 프리셋 추가</h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">자주 사용하는 프롬프트를 저장하세요</p>
        </div>
      </div>
    </div>

    <!-- Add/Edit Preset Modal -->
    <Teleport to="body">
      <div
        v-if="showModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ai-preset-modal-title"
      >
        <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="closeModal" />
        <div
          class="relative max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white dark:bg-gray-800 shadow-xl"
          @keydown.escape="closeModal"
        >
          <!-- Modal Header -->
          <div class="flex items-center justify-between border-b dark:border-gray-700 px-6 py-4">
            <h2 id="ai-preset-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ editingPreset ? '프리셋 수정' : '새 프리셋 추가' }}
            </h2>
            <button
              class="rounded-lg p-2 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
              aria-label="모달 닫기"
              @click="closeModal"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Modal Body -->
          <div class="p-6 space-y-4">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">프리셋 이름</label>
              <input
                v-model="presetForm.name"
                type="text"
                class="input-field"
                placeholder="예: 유튜브 SEO 최적화"
              />
            </div>

            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">도구 유형</label>
              <select v-model="presetForm.toolType" class="input-field">
                <option value="">선택</option>
                <option value="meta">제목/설명 생성</option>
                <option value="hashtags">해시태그 추천</option>
                <option value="ideas">콘텐츠 아이디어</option>
                <option value="report">성과 리포트</option>
                <option value="stt">영상 STT 변환</option>
                <option value="analyze">스크립트 분석</option>
                <option value="reply">댓글 답변 생성</option>
                <option value="schedule">업로드 시간 추천</option>
              </select>
            </div>

            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">설명</label>
              <input
                v-model="presetForm.description"
                type="text"
                class="input-field"
                placeholder="프리셋에 대한 간단한 설명"
              />
            </div>

            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">프롬프트</label>
              <textarea
                v-model="presetForm.prompt"
                class="input-field min-h-[120px] resize-y"
                placeholder="저장할 프롬프트를 입력하세요..."
              />
            </div>

            <!-- Actions -->
            <div class="flex justify-end gap-3 pt-2">
              <button class="btn-secondary" @click="closeModal">취소</button>
              <button
                class="btn-primary"
                :disabled="!isFormValid"
                @click="handleSave"
              >
                {{ editingPreset ? '수정' : '추가' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { PlusIcon, PencilIcon, TrashIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useAiHistoryStore, type AiPreset } from '@/stores/aiHistory'
import { useNotification } from '@/composables/useNotification'

const historyStore = useAiHistoryStore()
const { success } = useNotification()

const emit = defineEmits<{
  presetSelected: [preset: AiPreset]
}>()

const presets = computed(() => historyStore.presets)

const showModal = ref(false)
const editingPreset = ref<AiPreset | null>(null)
const presetForm = ref({
  name: '',
  toolType: '',
  prompt: '',
  description: '',
})

const isFormValid = computed(() => {
  return presetForm.value.name.trim() !== '' &&
    presetForm.value.toolType !== '' &&
    presetForm.value.prompt.trim() !== '' &&
    presetForm.value.description.trim() !== ''
})

function getToolTypeLabel(toolType: string): string {
  const labels: Record<string, string> = {
    meta: '제목/설명',
    hashtags: '해시태그',
    ideas: '아이디어',
    report: '리포트',
    stt: 'STT',
    analyze: '분석',
    reply: '댓글 답변',
    schedule: '시간 추천',
  }
  return labels[toolType] || toolType
}

function handlePresetClick(preset: AiPreset) {
  emit('presetSelected', preset)
}

function handleAddNew() {
  editingPreset.value = null
  presetForm.value = {
    name: '',
    toolType: '',
    prompt: '',
    description: '',
  }
  showModal.value = true
}

function handleEdit(preset: AiPreset) {
  if (preset.isDefault) return
  editingPreset.value = preset
  presetForm.value = {
    name: preset.name,
    toolType: preset.toolType,
    prompt: preset.prompt,
    description: preset.description,
  }
  showModal.value = true
}

function handleDelete(preset: AiPreset) {
  if (preset.isDefault) return
  if (confirm(`"${preset.name}" 프리셋을 삭제하시겠습니까?`)) {
    historyStore.removePreset(preset.id)
    success('프리셋이 삭제되었습니다')
  }
}

function handleSave() {
  if (!isFormValid.value) return

  if (editingPreset.value) {
    // Update existing preset
    historyStore.updatePreset(editingPreset.value.id, {
      name: presetForm.value.name,
      toolType: presetForm.value.toolType,
      prompt: presetForm.value.prompt,
      description: presetForm.value.description,
    })
    success('프리셋이 수정되었습니다')
  } else {
    // Add new preset
    historyStore.addPreset({
      name: presetForm.value.name,
      toolType: presetForm.value.toolType,
      prompt: presetForm.value.prompt,
      description: presetForm.value.description,
    })
    success('프리셋이 추가되었습니다')
  }

  closeModal()
}

function closeModal() {
  showModal.value = false
  editingPreset.value = null
  presetForm.value = {
    name: '',
    toolType: '',
    prompt: '',
    description: '',
  }
}
</script>
