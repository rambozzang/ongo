<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { PhotoIcon, DocumentTextIcon } from '@heroicons/vue/24/outline'
import type { Watermark, ImageWatermark, TextWatermark, WatermarkPosition } from '@/types/watermark'
import WatermarkPositionGrid from './WatermarkPositionGrid.vue'

const props = defineProps<{
  modelValue?: Watermark
}>()

const emit = defineEmits<{
  'update:watermark': [watermark: Watermark]
}>()

const activeTab = ref<'image' | 'text'>(props.modelValue?.type ?? 'text')

const imageWatermark = ref<ImageWatermark>({
  type: 'image',
  imageUrl: '',
  fileName: '',
  position: 'bottom-right',
  size: 15,
  opacity: 80,
  margin: 16,
})

const textWatermark = ref<TextWatermark>({
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
})

// Initialize from modelValue
if (props.modelValue) {
  if (props.modelValue.type === 'image') {
    imageWatermark.value = { ...props.modelValue }
  } else {
    textWatermark.value = { ...props.modelValue }
  }
}

const fontFamilies = [
  { value: 'Pretendard', label: 'Pretendard' },
  { value: 'Noto Sans KR', label: 'Noto Sans KR' },
  { value: 'Arial', label: 'Arial' },
  { value: 'Helvetica', label: 'Helvetica' },
  { value: 'Georgia', label: 'Georgia' },
  { value: 'Verdana', label: 'Verdana' },
  { value: 'Courier New', label: 'Courier New' },
]

const presetColors = [
  '#FFFFFF',
  '#000000',
  '#FF6B6B',
  '#4ECDC4',
  '#FFE66D',
  '#45B7D1',
  '#96CEB4',
  '#FFEAA7',
  '#DDA0DD',
  '#FF8A65',
  '#A5D6A7',
  '#90CAF9',
]

const currentWatermark = computed<Watermark>(() => {
  return activeTab.value === 'image' ? imageWatermark.value : textWatermark.value
})

// Watch for changes and emit
watch(
  [activeTab, imageWatermark, textWatermark],
  () => {
    emit('update:watermark', currentWatermark.value)
  },
  { deep: true }
)

// Watch modelValue for external changes
watch(
  () => props.modelValue,
  (newVal) => {
    if (!newVal) return
    if (newVal.type === 'image') {
      imageWatermark.value = { ...newVal }
      activeTab.value = 'image'
    } else {
      textWatermark.value = { ...newVal }
      activeTab.value = 'text'
    }
  }
)

const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerFileUpload() {
  fileInputRef.value?.click()
}

function handleFileSelect(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // Validate file type
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    alert('PNG 또는 JPG 파일만 업로드 가능합니다.')
    return
  }

  // Validate file size (max 2MB)
  if (file.size > 2 * 1024 * 1024) {
    alert('파일 크기는 2MB 이하여야 합니다.')
    return
  }

  const reader = new FileReader()
  reader.onload = (e) => {
    imageWatermark.value.imageUrl = e.target?.result as string
    imageWatermark.value.fileName = file.name
  }
  reader.readAsDataURL(file)

  // Reset input so same file can be selected again
  target.value = ''
}

function removeImage() {
  imageWatermark.value.imageUrl = ''
  imageWatermark.value.fileName = ''
}

function updateImagePosition(position: WatermarkPosition) {
  imageWatermark.value.position = position
}

function updateTextPosition(position: WatermarkPosition) {
  textWatermark.value.position = position
}

function selectPresetColor(color: string) {
  textWatermark.value.color = color
}
</script>

<template>
  <div class="space-y-6">
    <!-- Tab Switcher -->
    <div class="flex gap-1 border-b border-gray-200 dark:border-gray-700">
      <button
        type="button"
        class="relative inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors focus:outline-none"
        :class="
          activeTab === 'image'
            ? 'text-primary-600 dark:text-primary-400'
            : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
        "
        @click="activeTab = 'image'"
      >
        <PhotoIcon class="h-5 w-5" />
        <span>이미지 워터마크</span>
        <div
          v-if="activeTab === 'image'"
          class="absolute bottom-0 left-0 right-0 h-0.5 bg-primary-600 dark:bg-primary-400"
        />
      </button>
      <button
        type="button"
        class="relative inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors focus:outline-none"
        :class="
          activeTab === 'text'
            ? 'text-primary-600 dark:text-primary-400'
            : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
        "
        @click="activeTab = 'text'"
      >
        <DocumentTextIcon class="h-5 w-5" />
        <span>텍스트 워터마크</span>
        <div
          v-if="activeTab === 'text'"
          class="absolute bottom-0 left-0 right-0 h-0.5 bg-primary-600 dark:bg-primary-400"
        />
      </button>
    </div>

    <!-- Image Watermark Tab -->
    <div v-if="activeTab === 'image'" class="space-y-5">
      <!-- Image Upload Area -->
      <div>
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          워터마크 이미지
        </label>
        <input
          ref="fileInputRef"
          type="file"
          accept=".png,.jpg,.jpeg"
          class="hidden"
          @change="handleFileSelect"
        />
        <div v-if="imageWatermark.imageUrl" class="space-y-3">
          <div class="relative inline-block">
            <img
              :src="imageWatermark.imageUrl"
              :alt="imageWatermark.fileName"
              class="max-h-32 rounded-lg border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 object-contain p-2"
            />
            <button
              type="button"
              class="absolute -top-2 -right-2 w-6 h-6 bg-red-500 hover:bg-red-600 text-white rounded-full flex items-center justify-center text-xs transition-colors"
              title="이미지 제거"
              @click="removeImage"
            >
              &times;
            </button>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ imageWatermark.fileName }}</p>
          <button
            type="button"
            class="text-sm text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 font-medium"
            @click="triggerFileUpload"
          >
            이미지 변경
          </button>
        </div>
        <button
          v-else
          type="button"
          class="w-full p-8 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg hover:border-primary-400 dark:hover:border-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/10 transition-colors flex flex-col items-center gap-2"
          @click="triggerFileUpload"
        >
          <PhotoIcon class="w-10 h-10 text-gray-400 dark:text-gray-500" />
          <span class="text-sm font-medium text-gray-600 dark:text-gray-400">클릭하여 이미지 업로드</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">PNG, JPG (최대 2MB)</span>
        </button>
      </div>

      <!-- Position Grid -->
      <WatermarkPositionGrid
        :model-value="imageWatermark.position"
        @update:model-value="updateImagePosition"
      />

      <!-- Size Slider -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">크기</label>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ imageWatermark.size }}%</span>
        </div>
        <input
          v-model.number="imageWatermark.size"
          type="range"
          :min="5"
          :max="50"
          :step="1"
          class="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>5%</span>
          <span>50%</span>
        </div>
      </div>

      <!-- Opacity Slider -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">투명도</label>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ imageWatermark.opacity }}%</span>
        </div>
        <input
          v-model.number="imageWatermark.opacity"
          type="range"
          :min="0"
          :max="100"
          :step="1"
          class="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>0%</span>
          <span>100%</span>
        </div>
      </div>

      <!-- Margin Slider -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">여백</label>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ imageWatermark.margin }}px</span>
        </div>
        <input
          v-model.number="imageWatermark.margin"
          type="range"
          :min="0"
          :max="50"
          :step="1"
          class="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>0px</span>
          <span>50px</span>
        </div>
      </div>
    </div>

    <!-- Text Watermark Tab -->
    <div v-if="activeTab === 'text'" class="space-y-5">
      <!-- Text Input -->
      <div class="space-y-2">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
          워터마크 텍스트
        </label>
        <input
          v-model="textWatermark.text"
          type="text"
          placeholder="워터마크에 표시할 텍스트를 입력하세요"
          class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
        />
      </div>

      <!-- Font Family -->
      <div class="space-y-2">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
          글꼴
        </label>
        <select
          v-model="textWatermark.fontFamily"
          class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
        >
          <option
            v-for="font in fontFamilies"
            :key="font.value"
            :value="font.value"
            :style="{ fontFamily: font.value }"
          >
            {{ font.label }}
          </option>
        </select>
      </div>

      <!-- Font Size Slider -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">글꼴 크기</label>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ textWatermark.fontSize }}px</span>
        </div>
        <input
          v-model.number="textWatermark.fontSize"
          type="range"
          :min="12"
          :max="72"
          :step="1"
          class="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>12px</span>
          <span>72px</span>
        </div>
      </div>

      <!-- Color Picker -->
      <div class="space-y-2">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
          색상
        </label>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="color in presetColors"
            :key="color"
            type="button"
            class="w-8 h-8 rounded-lg border-2 transition-all duration-150 hover:scale-110"
            :class="
              textWatermark.color === color
                ? 'border-primary-500 ring-2 ring-primary-300 dark:ring-primary-700 scale-110'
                : 'border-gray-300 dark:border-gray-600'
            "
            :style="{ backgroundColor: color }"
            :title="color"
            @click="selectPresetColor(color)"
          ></button>
        </div>
        <div class="flex items-center gap-2 mt-2">
          <input
            v-model="textWatermark.color"
            type="color"
            class="w-10 h-10 rounded cursor-pointer border-2 border-gray-300 dark:border-gray-600"
          />
          <input
            v-model="textWatermark.color"
            type="text"
            placeholder="#FFFFFF"
            class="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm font-mono focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
        </div>
      </div>

      <!-- Bold / Italic Toggles -->
      <div class="space-y-2">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
          스타일
        </label>
        <div class="flex gap-2">
          <button
            type="button"
            class="px-4 py-2 text-sm font-bold rounded-md border transition-colors"
            :class="
              textWatermark.bold
                ? 'bg-primary-600 text-white border-primary-600 dark:bg-primary-500 dark:border-primary-500'
                : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            @click="textWatermark.bold = !textWatermark.bold"
          >
            B
          </button>
          <button
            type="button"
            class="px-4 py-2 text-sm italic rounded-md border transition-colors"
            :class="
              textWatermark.italic
                ? 'bg-primary-600 text-white border-primary-600 dark:bg-primary-500 dark:border-primary-500'
                : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            @click="textWatermark.italic = !textWatermark.italic"
          >
            I
          </button>
        </div>
      </div>

      <!-- Position Grid -->
      <WatermarkPositionGrid
        :model-value="textWatermark.position"
        @update:model-value="updateTextPosition"
      />

      <!-- Opacity Slider -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">투명도</label>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ textWatermark.opacity }}%</span>
        </div>
        <input
          v-model.number="textWatermark.opacity"
          type="range"
          :min="0"
          :max="100"
          :step="1"
          class="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>0%</span>
          <span>100%</span>
        </div>
      </div>

      <!-- Margin Slider -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">여백</label>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ textWatermark.margin }}px</span>
        </div>
        <input
          v-model.number="textWatermark.margin"
          type="range"
          :min="0"
          :max="50"
          :step="1"
          class="w-full h-2 bg-gray-200 dark:bg-gray-600 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>0px</span>
          <span>50px</span>
        </div>
      </div>
    </div>
  </div>
</template>
