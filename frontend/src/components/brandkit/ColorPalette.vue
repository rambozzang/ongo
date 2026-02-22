<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { PlusIcon, PencilIcon, TrashIcon, ClipboardIcon, CheckIcon } from '@heroicons/vue/24/outline'
import type { BrandColor } from '@/types/brandkit'

const props = defineProps<{
  colors: BrandColor[]
}>()

const emit = defineEmits<{
  add: [color: Omit<BrandColor, 'id'>]
  update: [id: number, updates: Partial<BrandColor>]
  remove: [id: number]
}>()

const isAdding = ref(false)
const editingId = ref<number | null>(null)
const copiedId = ref<number | null>(null)

const newColor = ref({
  name: '',
  hex: '#000000',
  usage: '주요 색상',
})

const editColor = ref({
  name: '',
  hex: '#000000',
  usage: '',
})

const usageOptions = ['주요 색상', '보조 색상', '강조 색상', '배경색', '본문 텍스트', '보조 텍스트']

function startAdd() {
  isAdding.value = true
  newColor.value = {
    name: '',
    hex: '#000000',
    usage: '주요 색상',
  }
}

function cancelAdd() {
  isAdding.value = false
}

function confirmAdd() {
  if (newColor.value.name.trim()) {
    emit('add', { ...newColor.value })
    isAdding.value = false
  }
}

function startEdit(color: BrandColor) {
  editingId.value = color.id
  editColor.value = {
    name: color.name,
    hex: color.hex,
    usage: color.usage,
  }
}

function cancelEdit() {
  editingId.value = null
}

function confirmEdit(id: number) {
  emit('update', id, { ...editColor.value })
  editingId.value = null
}

function handleRemove(id: number) {
  if (confirm('이 색상을 삭제하시겠습니까?')) {
    emit('remove', id)
  }
}

let copyTimeout: ReturnType<typeof setTimeout> | null = null

async function copyHex(hex: string, id: number) {
  try {
    await navigator.clipboard.writeText(hex)
    copiedId.value = id
    if (copyTimeout) clearTimeout(copyTimeout)
    copyTimeout = setTimeout(() => {
      copiedId.value = null
    }, 2000)
  } catch {
    // Clipboard API failed
  }
}

onUnmounted(() => {
  if (copyTimeout) clearTimeout(copyTimeout)
})
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
      <div
        v-for="color in props.colors"
        :key="color.id"
        class="group relative bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 hover:shadow-md transition-shadow"
      >
        <template v-if="editingId === color.id">
          <div class="space-y-3">
            <div class="flex items-center gap-2">
              <input
                v-model="editColor.hex"
                type="color"
                class="w-12 h-12 rounded cursor-pointer border-2 border-gray-300 dark:border-gray-600"
              />
              <div
                class="flex-1 h-12 rounded border border-gray-300 dark:border-gray-600"
                :style="{ backgroundColor: editColor.hex }"
              ></div>
            </div>
            <input
              v-model="editColor.name"
              type="text"
              placeholder="색상 이름"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <input
              v-model="editColor.hex"
              type="text"
              placeholder="#000000"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm font-mono focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <select
              v-model="editColor.usage"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option v-for="option in usageOptions" :key="option" :value="option">
                {{ option }}
              </option>
            </select>
            <div class="flex gap-2">
              <button
                @click="confirmEdit(color.id)"
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
          <div class="space-y-3">
            <div
              class="w-full h-20 rounded-lg border-2 border-gray-200 dark:border-gray-600 cursor-pointer"
              :style="{ backgroundColor: color.hex }"
              @click="copyHex(color.hex, color.id)"
              :title="`클릭하여 ${color.hex} 복사`"
            ></div>
            <div>
              <h4 class="font-medium text-gray-900 dark:text-gray-100 text-sm truncate">
                {{ color.name }}
              </h4>
              <div class="flex items-center gap-1 mt-1">
                <p class="text-xs text-gray-600 dark:text-gray-400 font-mono">
                  {{ color.hex }}
                </p>
                <button
                  @click="copyHex(color.hex, color.id)"
                  class="p-0.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors"
                  :title="`${color.hex} 복사`"
                >
                  <CheckIcon v-if="copiedId === color.id" class="w-3 h-3 text-green-600 dark:text-green-400" />
                  <ClipboardIcon v-else class="w-3 h-3 text-gray-400 dark:text-gray-500" />
                </button>
              </div>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {{ color.usage }}
              </p>
            </div>
          </div>
          <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              @click="startEdit(color)"
              class="p-1.5 bg-white dark:bg-gray-700 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-md shadow-sm border border-gray-200 dark:border-gray-600 transition-colors"
              title="편집"
            >
              <PencilIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
            </button>
            <button
              @click="handleRemove(color.id)"
              class="p-1.5 bg-white dark:bg-gray-700 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-md shadow-sm border border-gray-200 dark:border-gray-600 transition-colors"
              title="삭제"
            >
              <TrashIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
            </button>
          </div>
        </template>
      </div>

      <!-- Add New Color Card -->
      <div
        v-if="isAdding"
        class="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-blue-400 dark:border-blue-600 p-4"
      >
        <div class="space-y-3">
          <div class="flex items-center gap-2">
            <input
              v-model="newColor.hex"
              type="color"
              class="w-12 h-12 rounded cursor-pointer border-2 border-gray-300 dark:border-gray-600"
            />
            <div
              class="flex-1 h-12 rounded border border-gray-300 dark:border-gray-600"
              :style="{ backgroundColor: newColor.hex }"
            ></div>
          </div>
          <input
            v-model="newColor.name"
            type="text"
            placeholder="색상 이름"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
          <input
            v-model="newColor.hex"
            type="text"
            placeholder="#000000"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm font-mono focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
          <select
            v-model="newColor.usage"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option v-for="option in usageOptions" :key="option" :value="option">
              {{ option }}
            </option>
          </select>
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
        class="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 p-4 hover:border-blue-400 dark:hover:border-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/10 transition-colors flex flex-col items-center justify-center gap-2 min-h-[180px]"
      >
        <PlusIcon class="w-8 h-8 text-gray-400 dark:text-gray-500" />
        <span class="text-sm font-medium text-gray-600 dark:text-gray-400">색상 추가</span>
      </button>
    </div>
  </div>
</template>
