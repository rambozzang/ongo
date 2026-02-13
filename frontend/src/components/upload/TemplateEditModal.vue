<template>
  <Transition name="backdrop">
    <div
      class="fixed inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm tablet:items-center"
      role="dialog"
      aria-modal="true"
      aria-labelledby="template-edit-modal-title"
      @click.self="emit('close')"
    >
      <Transition name="sheet">
        <div
          class="flex max-h-[90vh] w-full flex-col rounded-t-2xl bg-white dark:bg-gray-800 shadow-2xl tablet:max-w-2xl tablet:rounded-2xl"
          @click.stop
          @keydown.escape="emit('close')"
        >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-gray-200 dark:border-gray-700 px-6 py-4">
        <h3 id="template-edit-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ template?.id ? '템플릿 수정' : '새 템플릿' }}
        </h3>
        <button
          class="rounded-lg p-2 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          aria-label="모달 닫기"
          @click="emit('close')"
        >
          <XMarkIcon class="h-6 w-6" />
        </button>
      </div>

      <!-- Form -->
      <div class="flex-1 overflow-y-auto px-6 py-6">
        <div class="space-y-5">
          <!-- Name -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              템플릿 이름 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.name"
              type="text"
              maxlength="50"
              class="input-field"
              placeholder="예: YouTube Vlog"
            />
            <p class="mt-1 text-right text-xs text-gray-400 dark:text-gray-500">
              {{ formData.name.length }}/50
            </p>
          </div>

          <!-- Description -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">설명</label>
            <input
              v-model="formData.description"
              type="text"
              maxlength="100"
              class="input-field"
              placeholder="이 템플릿에 대한 간단한 설명을 입력하세요"
            />
          </div>

          <!-- Title Pattern -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              제목 패턴 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.titlePattern"
              type="text"
              maxlength="100"
              class="input-field"
              placeholder="{title}을 사용하여 동적 제목을 만들 수 있습니다"
            />
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              <span class="font-medium">{title}</span> 플레이스홀더를 사용하면 실제 제목이 자동으로 삽입됩니다
            </p>
            <p v-if="formData.titlePattern" class="mt-1 text-xs text-primary-600 dark:text-primary-400">
              예시: {{ previewTitle }}
            </p>
          </div>

          <!-- Description Template -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              설명 템플릿 <span class="text-red-500">*</span>
            </label>
            <textarea
              v-model="formData.descriptionTemplate"
              rows="6"
              class="input-field resize-none"
              placeholder="영상 설명을 입력하세요. {title}을 사용할 수 있습니다."
            />
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              <span class="font-medium">{title}</span> 플레이스홀더를 사용하면 실제 제목이 자동으로 삽입됩니다
            </p>
          </div>

          <!-- Tags -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              태그 <span class="text-xs text-gray-400 dark:text-gray-500">(최대 30개, Enter로 추가)</span>
            </label>
            <div
              class="flex min-h-[42px] flex-wrap gap-1.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 focus-within:border-primary-500 focus-within:ring-1 focus-within:ring-primary-500"
            >
              <span
                v-for="(tag, i) in formData.tags"
                :key="i"
                class="inline-flex items-center gap-1 rounded-full bg-primary-100 dark:bg-primary-900/30 px-2.5 py-0.5 text-xs font-medium text-primary-700 dark:text-primary-400"
              >
                {{ tag }}
                <button
                  type="button"
                  class="text-primary-500 hover:text-primary-700"
                  @click="removeTag(i)"
                >
                  <XMarkIcon class="h-3.5 w-3.5" />
                </button>
              </span>
              <input
                v-if="formData.tags.length < 30"
                v-model="tagInput"
                type="text"
                class="min-w-[120px] flex-1 border-none bg-transparent p-0 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-0"
                placeholder="태그 입력 후 Enter"
                @keydown.enter.prevent="addTag"
                @keydown.,.prevent="addTag"
              />
            </div>
            <p class="mt-1 text-right text-xs text-gray-400 dark:text-gray-500">
              {{ formData.tags.length }}/30
            </p>
          </div>

          <!-- Category -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">카테고리</label>
            <select v-model="formData.category" class="input-field">
              <option value="">카테고리 선택</option>
              <option v-for="cat in CATEGORIES" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </div>

          <!-- Visibility -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">공개 범위</label>
            <div class="flex gap-4">
              <label
                v-for="opt in VISIBILITY_OPTIONS"
                :key="opt.value"
                class="flex cursor-pointer items-center gap-2"
              >
                <input
                  v-model="formData.visibility"
                  type="radio"
                  name="visibility"
                  :value="opt.value"
                  class="text-primary-600 focus:ring-primary-500"
                />
                <span class="text-sm text-gray-700 dark:text-gray-300">{{ opt.label }}</span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 border-t border-gray-200 dark:border-gray-700 px-6 py-4">
        <button class="btn-secondary" @click="emit('close')">
          취소
        </button>
        <button
          :disabled="!isValid"
          class="btn-primary"
          @click="handleSave"
        >
          {{ template?.id ? '수정' : '생성' }}
        </button>
      </div>
        </div>
      </Transition>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import type { MetadataTemplate } from '@/types/template'
import type { Visibility } from '@/types/video'

const props = defineProps<{
  template?: MetadataTemplate | null
}>()

const emit = defineEmits<{
  save: [data: Omit<MetadataTemplate, 'id' | 'createdAt' | 'updatedAt'>]
  close: []
}>()

const CATEGORIES = [
  '엔터테인먼트',
  '음악',
  '게임',
  '스포츠',
  '뉴스/정치',
  '교육',
  '과학기술',
  '여행/이벤트',
  '인물/블로그',
  '코미디',
  '영화/애니메이션',
  '자동차',
  '동물',
  '패션/뷰티',
  '푸드',
  '일상/Vlog',
]

const VISIBILITY_OPTIONS: { value: Visibility; label: string }[] = [
  { value: 'PUBLIC', label: '공개' },
  { value: 'UNLISTED', label: '일부 공개' },
  { value: 'PRIVATE', label: '비공개' },
]

const formData = reactive<{
  name: string
  description: string
  titlePattern: string
  descriptionTemplate: string
  tags: string[]
  category: string
  visibility: Visibility
}>({
  name: '',
  description: '',
  titlePattern: '',
  descriptionTemplate: '',
  tags: [],
  category: '',
  visibility: 'PUBLIC',
})

const tagInput = ref('')

const isValid = computed(() => {
  return (
    formData.name.trim().length > 0 &&
    formData.titlePattern.trim().length > 0 &&
    formData.descriptionTemplate.trim().length > 0
  )
})

const previewTitle = computed(() => {
  return formData.titlePattern.replace('{title}', '샘플 제목')
})

onMounted(() => {
  if (props.template) {
    formData.name = props.template.name
    formData.description = props.template.description || ''
    formData.titlePattern = props.template.titlePattern
    formData.descriptionTemplate = props.template.descriptionTemplate
    formData.tags = [...props.template.tags]
    formData.category = props.template.category
    formData.visibility = props.template.visibility as Visibility
  }
})

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !formData.tags.includes(tag) && formData.tags.length < 30) {
    formData.tags.push(tag)
  }
  tagInput.value = ''
}

function removeTag(index: number) {
  formData.tags.splice(index, 1)
}

function handleSave() {
  if (!isValid.value) return

  emit('save', {
    name: formData.name.trim(),
    description: formData.description.trim() || '',
    titlePattern: formData.titlePattern.trim(),
    descriptionTemplate: formData.descriptionTemplate.trim(),
    tags: formData.tags,
    category: formData.category,
    visibility: formData.visibility,
  })
}
</script>
