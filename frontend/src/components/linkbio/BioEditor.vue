<template>
  <div class="space-y-6">
    <!-- Profile Section -->
    <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
      <h3 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">프로필</h3>
      <div class="space-y-4">
        <div class="flex items-center gap-4">
          <img
            :src="page.avatarUrl"
            :alt="page.displayName"
            class="h-16 w-16 rounded-full border-2 border-gray-200 dark:border-gray-600"
          />
          <div class="flex-1">
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
              표시 이름
            </label>
            <input
              v-model="localDisplayName"
              type="text"
              class="w-full rounded-md border border-gray-300 px-3 py-2 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
              @blur="updateProfile"
            />
          </div>
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
            소개
          </label>
          <textarea
            v-model="localBio"
            rows="3"
            class="w-full rounded-md border border-gray-300 px-3 py-2 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
            @blur="updateProfile"
          />
        </div>
      </div>
    </div>

    <!-- Theme Section -->
    <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
      <h3 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">테마</h3>
      <ThemeSelector :selected-theme="page.theme" @select="$emit('change-theme', $event)" />

      <!-- Color Customization -->
      <div class="mt-6 grid grid-cols-2 gap-4">
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
            배경색
          </label>
          <input
            :value="page.backgroundColor"
            type="color"
            class="h-10 w-full cursor-pointer rounded-md border border-gray-300 dark:border-gray-600"
            @input="updateColor('backgroundColor', ($event.target as HTMLInputElement).value)"
          />
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
            텍스트 색
          </label>
          <input
            :value="page.textColor"
            type="color"
            class="h-10 w-full cursor-pointer rounded-md border border-gray-300 dark:border-gray-600"
            @input="updateColor('textColor', ($event.target as HTMLInputElement).value)"
          />
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
            버튼 배경
          </label>
          <input
            :value="page.buttonColor"
            type="color"
            class="h-10 w-full cursor-pointer rounded-md border border-gray-300 dark:border-gray-600"
            @input="updateColor('buttonColor', ($event.target as HTMLInputElement).value)"
          />
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">
            버튼 텍스트
          </label>
          <input
            :value="page.buttonTextColor"
            type="color"
            class="h-10 w-full cursor-pointer rounded-md border border-gray-300 dark:border-gray-600"
            @input="updateColor('buttonTextColor', ($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>
    </div>

    <!-- Blocks Section -->
    <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-title font-semibold text-gray-900 dark:text-gray-100">블록</h3>
        <button
          class="flex items-center gap-2 rounded-md bg-primary-600 px-4 py-2 text-body font-medium text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600"
          @click="showBlockSelector = !showBlockSelector"
        >
          <PlusIcon class="h-4 w-4" />
          블록 추가
        </button>
      </div>

      <!-- Block Type Selector -->
      <div v-if="showBlockSelector" class="mb-4">
        <BlockTypeSelector
          @select="handleAddBlock"
        />
      </div>

      <!-- Block List -->
      <div v-if="page.blocks.length === 0" class="py-12 text-center text-gray-500 dark:text-gray-400">
        블록을 추가하여 시작하세요
      </div>
      <div v-else class="space-y-3">
        <BioBlockItem
          v-for="(block, index) in page.blocks"
          :key="block.id"
          :block="block"
          @update="$emit('update-block', block.id, $event)"
          @toggle-visibility="$emit('toggle-visibility', block.id)"
          @delete="$emit('delete-block', block.id)"
          @dragstart="handleDragStart(index, $event)"
          @dragend="handleDragEnd"
          @drop="handleDrop(index, $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { PlusIcon } from '@heroicons/vue/24/outline'
import type { BioPage, ThemeStyle, BlockType, BioBlock } from '@/types/linkbio'
import ThemeSelector from './ThemeSelector.vue'
import BlockTypeSelector from './BlockTypeSelector.vue'
import BioBlockItem from './BioBlockItem.vue'

const props = defineProps<{
  page: BioPage
}>()

const emit = defineEmits<{
  'update-profile': [updates: Partial<Pick<BioPage, 'displayName' | 'bio'>>]
  'change-theme': [theme: ThemeStyle]
  'update-colors': [colors: Partial<Pick<BioPage, 'backgroundColor' | 'textColor' | 'buttonColor' | 'buttonTextColor'>>]
  'add-block': [type: BlockType]
  'update-block': [blockId: number, updates: Partial<BioBlock>]
  'delete-block': [blockId: number]
  'toggle-visibility': [blockId: number]
  'reorder-block': [fromIndex: number, toIndex: number]
}>()

const localDisplayName = ref(props.page.displayName)
const localBio = ref(props.page.bio)
const showBlockSelector = ref(false)

const draggedIndex = ref<number | null>(null)

watch(
  () => props.page,
  (page) => {
    localDisplayName.value = page.displayName
    localBio.value = page.bio
  }
)

const updateProfile = () => {
  emit('update-profile', {
    displayName: localDisplayName.value,
    bio: localBio.value,
  })
}

const updateColor = (key: 'backgroundColor' | 'textColor' | 'buttonColor' | 'buttonTextColor', value: string) => {
  emit('update-colors', { [key]: value })
}

const handleAddBlock = (type: BlockType) => {
  emit('add-block', type)
  showBlockSelector.value = false
}

const handleDragStart = (index: number, event: DragEvent) => {
  draggedIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

const handleDragEnd = () => {
  draggedIndex.value = null
}

const handleDrop = (dropIndex: number, event: DragEvent) => {
  event.preventDefault()
  if (draggedIndex.value !== null && draggedIndex.value !== dropIndex) {
    emit('reorder-block', draggedIndex.value, dropIndex)
  }
  draggedIndex.value = null
}
</script>
