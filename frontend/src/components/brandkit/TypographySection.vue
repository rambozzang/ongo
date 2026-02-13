<script setup lang="ts">
import { ref } from 'vue'
import { PlusIcon, PencilIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { BrandFont } from '@/types/brandkit'

const props = defineProps<{
  fonts: BrandFont[]
}>()

const emit = defineEmits<{
  add: [font: Omit<BrandFont, 'id'>]
  update: [id: number, updates: Partial<BrandFont>]
  remove: [id: number]
}>()

const isAdding = ref(false)
const editingId = ref<number | null>(null)

const newFont = ref({
  name: '',
  family: 'Pretendard',
  weight: '400',
  usage: '본문',
  sampleText: '샘플 텍스트를 입력하세요',
})

const editFont = ref({
  name: '',
  family: '',
  weight: '',
  usage: '',
  sampleText: '',
})

const fontFamilies = ['Pretendard', 'Noto Sans KR', 'system-ui', 'Arial', 'Helvetica']
const fontWeights = [
  { value: '300', label: 'Light (300)' },
  { value: '400', label: 'Regular (400)' },
  { value: '500', label: 'Medium (500)' },
  { value: '600', label: 'SemiBold (600)' },
  { value: '700', label: 'Bold (700)' },
  { value: '800', label: 'ExtraBold (800)' },
]
const usageOptions = ['제목', '소제목', '본문', '캡션']

function startAdd() {
  isAdding.value = true
  newFont.value = {
    name: '',
    family: 'Pretendard',
    weight: '400',
    usage: '본문',
    sampleText: '샘플 텍스트를 입력하세요',
  }
}

function cancelAdd() {
  isAdding.value = false
}

function confirmAdd() {
  if (newFont.value.name.trim() && newFont.value.sampleText.trim()) {
    emit('add', { ...newFont.value })
    isAdding.value = false
  }
}

function startEdit(font: BrandFont) {
  editingId.value = font.id
  editFont.value = {
    name: font.name,
    family: font.family,
    weight: font.weight,
    usage: font.usage,
    sampleText: font.sampleText,
  }
}

function cancelEdit() {
  editingId.value = null
}

function confirmEdit(id: number) {
  emit('update', id, { ...editFont.value })
  editingId.value = null
}

function handleRemove(id: number) {
  if (confirm('이 서체를 삭제하시겠습니까?')) {
    emit('remove', id)
  }
}

function getFontStyle(family: string, weight: string) {
  return {
    fontFamily: family,
    fontWeight: weight,
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-3">
      <div
        v-for="font in props.fonts"
        :key="font.id"
        class="group bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 hover:shadow-md transition-shadow"
      >
        <template v-if="editingId === font.id">
          <div class="space-y-3">
            <div class="grid grid-cols-2 gap-3">
              <input
                v-model="editFont.name"
                type="text"
                placeholder="서체 이름"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
              <select
                v-model="editFont.usage"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option v-for="option in usageOptions" :key="option" :value="option">
                  {{ option }}
                </option>
              </select>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <select
                v-model="editFont.family"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option v-for="family in fontFamilies" :key="family" :value="family">
                  {{ family }}
                </option>
              </select>
              <select
                v-model="editFont.weight"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option v-for="weight in fontWeights" :key="weight.value" :value="weight.value">
                  {{ weight.label }}
                </option>
              </select>
            </div>
            <input
              v-model="editFont.sampleText"
              type="text"
              placeholder="샘플 텍스트"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <div class="flex gap-2">
              <button
                @click="confirmEdit(font.id)"
                class="flex-1 px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md transition-colors"
              >
                저장
              </button>
              <button
                @click="cancelEdit"
                class="flex-1 px-3 py-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 text-sm font-medium rounded-md transition-colors"
              >
                취소
              </button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="flex items-start gap-4">
            <div class="flex-1 space-y-2">
              <div class="flex items-center gap-2">
                <h4 class="font-medium text-gray-900 dark:text-gray-100">
                  {{ font.name }}
                </h4>
                <span class="px-2 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-xs rounded-full">
                  {{ font.usage }}
                </span>
              </div>
              <p class="text-sm text-gray-600 dark:text-gray-400">
                {{ font.family }} · {{ fontWeights.find(w => w.value === font.weight)?.label || font.weight }}
              </p>
              <div
                class="mt-3 p-4 bg-gray-50 dark:bg-gray-900/50 rounded border border-gray-200 dark:border-gray-700"
                :style="getFontStyle(font.family, font.weight)"
              >
                <p class="text-gray-900 dark:text-gray-100 text-lg">
                  {{ font.sampleText }}
                </p>
              </div>
            </div>
            <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                @click="startEdit(font)"
                class="p-1.5 bg-white dark:bg-gray-700 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-md shadow-sm border border-gray-200 dark:border-gray-600 transition-colors"
                title="편집"
              >
                <PencilIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
              </button>
              <button
                @click="handleRemove(font.id)"
                class="p-1.5 bg-white dark:bg-gray-700 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-md shadow-sm border border-gray-200 dark:border-gray-600 transition-colors"
                title="삭제"
              >
                <TrashIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
              </button>
            </div>
          </div>
        </template>
      </div>

      <!-- Add New Font Form -->
      <div
        v-if="isAdding"
        class="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-blue-400 dark:border-blue-600 p-4"
      >
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <input
              v-model="newFont.name"
              type="text"
              placeholder="서체 이름"
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <select
              v-model="newFont.usage"
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option v-for="option in usageOptions" :key="option" :value="option">
                {{ option }}
              </option>
            </select>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <select
              v-model="newFont.family"
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option v-for="family in fontFamilies" :key="family" :value="family">
                {{ family }}
              </option>
            </select>
            <select
              v-model="newFont.weight"
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option v-for="weight in fontWeights" :key="weight.value" :value="weight.value">
                {{ weight.label }}
              </option>
            </select>
          </div>
          <input
            v-model="newFont.sampleText"
            type="text"
            placeholder="샘플 텍스트"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
          <div
            class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded border border-gray-200 dark:border-gray-700"
            :style="getFontStyle(newFont.family, newFont.weight)"
          >
            <p class="text-gray-900 dark:text-gray-100 text-lg">
              {{ newFont.sampleText }}
            </p>
          </div>
          <div class="flex gap-2">
            <button
              @click="confirmAdd"
              class="flex-1 px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md transition-colors"
            >
              추가
            </button>
            <button
              @click="cancelAdd"
              class="flex-1 px-3 py-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 text-sm font-medium rounded-md transition-colors"
            >
              취소
            </button>
          </div>
        </div>
      </div>

      <!-- Add Button -->
      <button
        v-if="!isAdding"
        @click="startAdd"
        class="w-full bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 p-4 hover:border-blue-400 dark:hover:border-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/10 transition-colors flex items-center justify-center gap-2"
      >
        <PlusIcon class="w-5 h-5 text-gray-400 dark:text-gray-500" />
        <span class="text-sm font-medium text-gray-600 dark:text-gray-400">서체 추가</span>
      </button>
    </div>
  </div>
</template>
