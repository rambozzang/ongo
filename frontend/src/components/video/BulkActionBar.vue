<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="translate-y-full opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition-all duration-200 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-full opacity-0"
    >
      <div
        v-if="selectedCount > 0"
        class="fixed bottom-20 left-1/2 z-20 -translate-x-1/2 tablet:bottom-6"
      >
        <div
          class="glass-elevated flex items-center gap-3 rounded-2xl border border-gray-200/50 bg-white/90 px-6 py-4 shadow-2xl backdrop-blur-xl dark:border-gray-700/50 dark:bg-gray-800/90"
        >
          <!-- Selected Count Badge -->
          <div
            class="flex items-center gap-2 rounded-lg bg-primary-100 px-3 py-1.5 dark:bg-primary-900/30"
          >
            <CheckCircleIcon class="h-4 w-4 text-primary-700 dark:text-primary-400" />
            <span class="text-sm font-semibold text-primary-700 dark:text-primary-400">
              {{ selectedCount }}개 선택됨
            </span>
          </div>

          <!-- Divider -->
          <div class="h-6 w-px bg-gray-300 dark:bg-gray-600" />

          <!-- Action Buttons -->
          <div class="flex items-center gap-2">
            <!-- Platform Publish -->
            <button
              class="btn-secondary inline-flex items-center gap-1.5 text-sm"
              @click="handlePublish"
            >
              <ArrowUpTrayIcon class="h-4 w-4" />
              플랫폼 게시
            </button>

            <!-- Category Change -->
            <div class="relative">
              <button
                class="btn-secondary inline-flex items-center gap-1.5 text-sm"
                @click="toggleTagMenu"
              >
                <TagIcon class="h-4 w-4" />
                카테고리 변경
              </button>

              <!-- Tag Menu Dropdown -->
              <Transition
                enter-active-class="transition-all duration-200 ease-out"
                enter-from-class="opacity-0 scale-95 -translate-y-2"
                enter-to-class="opacity-100 scale-100 translate-y-0"
                leave-active-class="transition-all duration-150 ease-in"
                leave-from-class="opacity-100 scale-100 translate-y-0"
                leave-to-class="opacity-0 scale-95 -translate-y-2"
              >
                <div
                  v-if="showTagMenu"
                  class="absolute bottom-full left-0 mb-2 w-64 origin-bottom-left"
                >
                  <div
                    class="card rounded-lg border border-gray-200 p-4 shadow-lg dark:border-gray-700"
                  >
                    <label class="mb-2 block text-xs font-medium text-gray-700 dark:text-gray-300">
                      태그 입력 (쉼표로 구분)
                    </label>
                    <input
                      ref="tagInput"
                      v-model="newTags"
                      type="text"
                      placeholder="예: 브이로그, 여행, 일상"
                      class="input mb-3 w-full text-sm"
                      @keydown.enter="handleAddTags"
                      @keydown.esc="closeTagMenu"
                    />
                    <div class="flex justify-end gap-2">
                      <button class="btn-secondary text-xs" @click="closeTagMenu">취소</button>
                      <button
                        class="btn-primary text-xs"
                        :disabled="!newTags.trim()"
                        @click="handleAddTags"
                      >
                        추가
                      </button>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- AI Batch -->
            <div class="relative">
              <button
                class="btn-secondary inline-flex items-center gap-1.5 text-sm"
                @click="toggleAiBatchMenu"
              >
                <SparklesIcon class="h-4 w-4" />
                AI 배치
              </button>

              <!-- AI Batch Dropdown -->
              <Transition
                enter-active-class="transition-all duration-200 ease-out"
                enter-from-class="opacity-0 scale-95 -translate-y-2"
                enter-to-class="opacity-100 scale-100 translate-y-0"
                leave-active-class="transition-all duration-150 ease-in"
                leave-from-class="opacity-100 scale-100 translate-y-0"
                leave-to-class="opacity-0 scale-95 -translate-y-2"
              >
                <div
                  v-if="showAiBatchMenu"
                  class="absolute bottom-full left-0 mb-2 w-72 origin-bottom-left"
                >
                  <div
                    class="card rounded-lg border border-gray-200 p-3 shadow-lg dark:border-gray-700"
                  >
                    <p class="mb-2 text-xs font-medium text-gray-700 dark:text-gray-300">
                      AI 작업 선택
                    </p>
                    <div class="space-y-1">
                      <button
                        v-for="op in BATCH_OPERATIONS"
                        :key="op.key"
                        class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                        @click="selectAiBatchOperation(op.key)"
                      >
                        <span class="text-gray-900 dark:text-gray-100">{{ op.label }}</span>
                        <span class="text-xs text-gray-500 dark:text-gray-400">
                          {{ op.creditCost * selectedCount }}크레딧
                        </span>
                      </button>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Export -->
            <button
              class="btn-secondary inline-flex items-center gap-1.5 text-sm"
              @click="handleExport"
            >
              <ArrowDownTrayIcon class="h-4 w-4" />
              내보내기
            </button>

            <!-- Delete -->
            <button
              class="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
              @click="handleDeleteClick"
            >
              <TrashIcon class="h-4 w-4" />
              삭제
            </button>
          </div>

          <!-- Divider -->
          <div class="h-6 w-px bg-gray-300 dark:bg-gray-600" />

          <!-- Clear Selection -->
          <button
            class="btn-secondary text-sm"
            @click="handleClearSelection"
          >
            전체 해제
          </button>
        </div>
      </div>
    </Transition>

    <!-- Click Outside Handler -->
    <div
      v-if="showTagMenu || showAiBatchMenu"
      class="fixed inset-0 z-10"
      @click="closeAllMenus"
    />

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      title="영상 삭제"
      :message="`정말 ${selectedCount}개 영상을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`"
      confirm-text="삭제"
      :danger="true"
      @confirm="confirmDelete"
    />
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import {
  TagIcon,
  TrashIcon,
  CheckCircleIcon,
  ArrowUpTrayIcon,
  ArrowDownTrayIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { BATCH_OPERATIONS } from '@/types/ai'
import type { AiBatchOperation } from '@/types/ai'

interface Props {
  selectedCount: number
}

interface Emits {
  (e: 'delete'): void
  (e: 'publish'): void
  (e: 'changeCategory', tags: string[]): void
  (e: 'export'): void
  (e: 'clearSelection'): void
  (e: 'aiBatch', operation: AiBatchOperation): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

// Tag menu
const showTagMenu = ref(false)
const newTags = ref('')
const tagInput = ref<HTMLInputElement>()

async function toggleTagMenu() {
  showTagMenu.value = !showTagMenu.value
  if (showTagMenu.value) {
    await nextTick()
    tagInput.value?.focus()
  }
}

function closeTagMenu() {
  showTagMenu.value = false
  newTags.value = ''
}

function handleAddTags() {
  const tags = newTags.value
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0)

  if (tags.length > 0) {
    emit('changeCategory', tags)
    closeTagMenu()
  }
}

// Publish action
function handlePublish() {
  emit('publish')
}

// Export action
function handleExport() {
  emit('export')
}

// Delete modal
const showDeleteModal = ref(false)

function handleDeleteClick() {
  showDeleteModal.value = true
}

function confirmDelete() {
  emit('delete')
  showDeleteModal.value = false
}

// Clear selection
function handleClearSelection() {
  emit('clearSelection')
}

// AI Batch menu
const showAiBatchMenu = ref(false)

function toggleAiBatchMenu() {
  showAiBatchMenu.value = !showAiBatchMenu.value
  if (showAiBatchMenu.value) {
    closeTagMenu()
  }
}

function closeAiBatchMenu() {
  showAiBatchMenu.value = false
}

function selectAiBatchOperation(operation: AiBatchOperation) {
  emit('aiBatch', operation)
  closeAiBatchMenu()
}

// Close all menus
function closeAllMenus() {
  closeTagMenu()
  closeAiBatchMenu()
}
</script>
