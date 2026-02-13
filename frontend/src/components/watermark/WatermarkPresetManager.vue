<script setup lang="ts">
import { ref } from 'vue'
import {
  PlusIcon,
  PencilIcon,
  TrashIcon,
  StarIcon,
  PhotoIcon,
  DocumentTextIcon,
  CheckIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import type { Watermark, WatermarkPreset } from '@/types/watermark'
import { useWatermarkStore } from '@/stores/watermark'
import WatermarkEditor from './WatermarkEditor.vue'
import WatermarkPreview from './WatermarkPreview.vue'

const store = useWatermarkStore()

const isAddingPreset = ref(false)
const newPresetName = ref('')
const editingNameId = ref<string | null>(null)
const editingNameValue = ref('')
const expandedPresetId = ref<string | null>(null)


const platforms = [
  { key: 'youtube', label: 'YouTube' },
  { key: 'tiktok', label: 'TikTok' },
  { key: 'instagram', label: 'Instagram Reels' },
  { key: 'naverclip', label: 'Naver Clip' },
]

const defaultTextWatermark: Watermark = {
  type: 'text',
  text: '',
  position: 'bottom-right',
  fontSize: 24,
  fontFamily: 'Pretendard',
  color: '#FFFFFF',
  opacity: 60,
  margin: 16,
  bold: true,
  italic: false,
}

function startAddPreset() {
  isAddingPreset.value = true
  newPresetName.value = ''
}

function cancelAddPreset() {
  isAddingPreset.value = false
  newPresetName.value = ''
}

function confirmAddPreset() {
  const name = newPresetName.value.trim()
  if (!name) return
  store.addPreset(name, { ...defaultTextWatermark } as Watermark)
  isAddingPreset.value = false
  newPresetName.value = ''
  // Expand the newly added preset for editing
  expandedPresetId.value = store.activePresetId
}

function startEditName(preset: WatermarkPreset) {
  editingNameId.value = preset.id
  editingNameValue.value = preset.name
}

function cancelEditName() {
  editingNameId.value = null
  editingNameValue.value = ''
}

function confirmEditName(id: string) {
  const name = editingNameValue.value.trim()
  if (name) {
    store.updatePresetName(id, name)
  }
  editingNameId.value = null
  editingNameValue.value = ''
}

function handleDelete(id: string) {
  if (confirm('이 프리셋을 삭제하시겠습니까?')) {
    store.deletePreset(id)
    if (expandedPresetId.value === id) {
      expandedPresetId.value = null
    }
  }
}

function toggleExpand(id: string) {
  expandedPresetId.value = expandedPresetId.value === id ? null : id
  store.setActivePreset(id)
}

function handleWatermarkUpdate(presetId: string, watermark: Watermark) {
  store.updatePreset(presetId, watermark)
}

function togglePlatformOverride(presetId: string, platformKey: string) {
  const preset = store.presets.find(p => p.id === presetId)
  if (!preset) return

  if (preset.platformOverrides[platformKey]) {
    store.removePlatformOverride(presetId, platformKey)
  } else {
    // Clone the main watermark as the starting point for the override
    const overrideWatermark = JSON.parse(JSON.stringify(preset.watermark)) as Watermark
    store.setPlatformOverride(presetId, platformKey, overrideWatermark)
  }
}

function handlePlatformWatermarkUpdate(presetId: string, platformKey: string, watermark: Watermark) {
  store.setPlatformOverride(presetId, platformKey, watermark)
}

function getWatermarkTypeIcon(watermark: Watermark) {
  return watermark.type === 'image' ? PhotoIcon : DocumentTextIcon
}

function getWatermarkTypeName(watermark: Watermark): string {
  return watermark.type === 'image' ? '이미지' : '텍스트'
}

function getPlatformOverrideCount(preset: WatermarkPreset): number {
  return Object.keys(preset.platformOverrides).length
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        워터마크 프리셋
      </h2>
      <button
        v-if="!isAddingPreset"
        type="button"
        class="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600 rounded-lg transition-colors"
        @click="startAddPreset"
      >
        <PlusIcon class="w-4 h-4" />
        새 프리셋 추가
      </button>
    </div>

    <!-- Add Preset Form -->
    <div
      v-if="isAddingPreset"
      class="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-primary-400 dark:border-primary-600 p-4"
    >
      <div class="space-y-3">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
          프리셋 이름
        </label>
        <input
          v-model="newPresetName"
          type="text"
          placeholder="프리셋 이름을 입력하세요"
          class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          @keydown.enter="confirmAddPreset"
          @keydown.escape="cancelAddPreset"
        />
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 px-3 py-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium rounded-md transition-colors"
            @click="confirmAddPreset"
          >
            추가
          </button>
          <button
            type="button"
            class="flex-1 px-3 py-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 text-sm font-medium rounded-md transition-colors"
            @click="cancelAddPreset"
          >
            취소
          </button>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-if="store.presets.length === 0 && !isAddingPreset"
      class="text-center py-12 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700"
    >
      <PhotoIcon class="w-12 h-12 text-gray-400 dark:text-gray-500 mx-auto mb-3" />
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
        저장된 워터마크 프리셋이 없습니다.
      </p>
      <button
        type="button"
        class="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600 rounded-lg transition-colors"
        @click="startAddPreset"
      >
        <PlusIcon class="w-4 h-4" />
        첫 번째 프리셋 만들기
      </button>
    </div>

    <!-- Preset List -->
    <div class="space-y-3">
      <div
        v-for="preset in store.presets"
        :key="preset.id"
        class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden transition-shadow hover:shadow-md"
      >
        <!-- Preset Card Header -->
        <div class="p-4">
          <div class="flex items-center gap-3">
            <!-- Type Icon -->
            <div class="flex-shrink-0 w-10 h-10 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center">
              <component
                :is="getWatermarkTypeIcon(preset.watermark)"
                class="w-5 h-5 text-gray-600 dark:text-gray-400"
              />
            </div>

            <!-- Name -->
            <div class="flex-1 min-w-0">
              <div v-if="editingNameId === preset.id" class="flex items-center gap-2">
                <input
                  v-model="editingNameValue"
                  type="text"
                  class="flex-1 px-2 py-1 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  @keydown.enter="confirmEditName(preset.id)"
                  @keydown.escape="cancelEditName"
                />
                <button
                  type="button"
                  class="p-1 text-green-600 hover:text-green-700 dark:text-green-400"
                  @click="confirmEditName(preset.id)"
                >
                  <CheckIcon class="w-4 h-4" />
                </button>
                <button
                  type="button"
                  class="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                  @click="cancelEditName"
                >
                  <XMarkIcon class="w-4 h-4" />
                </button>
              </div>
              <template v-else>
                <div class="flex items-center gap-2">
                  <h3
                    class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate cursor-pointer hover:text-primary-600 dark:hover:text-primary-400"
                    @click="toggleExpand(preset.id)"
                  >
                    {{ preset.name }}
                  </h3>
                  <span
                    v-if="preset.isDefault"
                    class="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300"
                  >
                    <StarIconSolid class="w-3 h-3" />
                    기본값
                  </span>
                </div>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                  {{ getWatermarkTypeName(preset.watermark) }}
                  <span v-if="getPlatformOverrideCount(preset) > 0">
                    &middot; 플랫폼 설정 {{ getPlatformOverrideCount(preset) }}개
                  </span>
                </p>
              </template>
            </div>

            <!-- Actions -->
            <div class="flex items-center gap-1">
              <button
                v-if="!preset.isDefault"
                type="button"
                class="p-1.5 rounded-md hover:bg-yellow-50 dark:hover:bg-yellow-900/20 transition-colors"
                title="기본값으로 설정"
                @click="store.setDefault(preset.id)"
              >
                <StarIcon class="w-4 h-4 text-gray-400 dark:text-gray-500 hover:text-yellow-500" />
              </button>
              <button
                type="button"
                class="p-1.5 rounded-md hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                title="이름 편집"
                @click="startEditName(preset)"
              >
                <PencilIcon class="w-4 h-4 text-gray-400 dark:text-gray-500" />
              </button>
              <button
                type="button"
                class="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                title="삭제"
                @click="handleDelete(preset.id)"
              >
                <TrashIcon class="w-4 h-4 text-gray-400 dark:text-gray-500" />
              </button>
            </div>
          </div>
        </div>

        <!-- Expanded Editor & Preview -->
        <div
          v-if="expandedPresetId === preset.id"
          class="border-t border-gray-200 dark:border-gray-700"
        >
          <div class="p-4 grid grid-cols-1 lg:grid-cols-2 gap-6">
            <!-- Editor -->
            <div>
              <WatermarkEditor
                :model-value="preset.watermark"
                @update:watermark="(wm: Watermark) => handleWatermarkUpdate(preset.id, wm)"
              />
            </div>

            <!-- Preview -->
            <div class="space-y-4">
              <WatermarkPreview :watermark="preset.watermark" />

              <!-- Platform Overrides -->
              <div class="space-y-3">
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
                  플랫폼별 설정
                </h4>
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  특정 플랫폼에 다른 워터마크 설정을 적용할 수 있습니다.
                </p>
                <div class="space-y-2">
                  <div
                    v-for="platform in platforms"
                    :key="platform.key"
                    class="bg-gray-50 dark:bg-gray-700/50 rounded-lg"
                  >
                    <div class="flex items-center justify-between px-3 py-2">
                      <span class="text-sm text-gray-700 dark:text-gray-300">
                        {{ platform.label }}
                      </span>
                      <label class="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          :checked="!!preset.platformOverrides[platform.key]"
                          class="sr-only peer"
                          @change="togglePlatformOverride(preset.id, platform.key)"
                        />
                        <div class="w-9 h-5 bg-gray-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:peer-focus:ring-primary-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all dark:after:border-gray-600 peer-checked:bg-primary-600 dark:peer-checked:bg-primary-500"></div>
                      </label>
                    </div>

                    <!-- Platform-specific editor (collapsed, shows when toggle is on) -->
                    <div
                      v-if="preset.platformOverrides[platform.key]"
                      class="px-3 pb-3 space-y-4 border-t border-gray-200 dark:border-gray-600 pt-3"
                    >
                      <WatermarkEditor
                        :model-value="preset.platformOverrides[platform.key]"
                        @update:watermark="(wm: Watermark) => handlePlatformWatermarkUpdate(preset.id, platform.key, wm)"
                      />
                      <WatermarkPreview
                        :watermark="preset.platformOverrides[platform.key]!"
                        :platform-name="platform.label"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
