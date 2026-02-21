<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  XMarkIcon,
  ArrowUpTrayIcon,
  ArrowDownTrayIcon,
  DocumentTextIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  ArrowLeftIcon,
  ArrowRightIcon,
  CloudArrowUpIcon,
} from '@heroicons/vue/24/outline'
import { useCsvImportStore } from '@/stores/csvImport'
import CsvPreviewTable from './CsvPreviewTable.vue'
import CsvTemplateInfo from './CsvTemplateInfo.vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  imported: []
}>()

const store = useCsvImportStore()

const currentStep = ref(1)
const selectedFile = ref<File | null>(null)
const dragOver = ref(false)
const fileError = ref<string | null>(null)
const importComplete = ref(false)
const fileInput = ref<HTMLInputElement>()

const ACCEPTED_EXTENSIONS = ['.csv', '.xlsx', '.xls']
const ACCEPTED_MIME_TYPES = [
  'text/csv',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-excel',
]

const canGoNext = computed(() => {
  if (currentStep.value === 1) {
    return selectedFile.value !== null && !fileError.value
  }
  if (currentStep.value === 2) {
    return store.parsedData !== null && store.parsedData.rows.length > 0 && (store.parsedData.validRows > 0 || store.parsedData.warningRows > 0)
  }
  return false
})

const stepTitle = computed(() => {
  switch (currentStep.value) {
    case 1:
      return '파일 업로드'
    case 2:
      return '미리보기 및 검증'
    case 3:
      return '가져오기'
    default:
      return ''
  }
})

// Reset state when modal is closed
watch(
  () => props.modelValue,
  (newVal) => {
    if (!newVal) {
      resetAll()
    }
  },
)

function resetAll() {
  currentStep.value = 1
  selectedFile.value = null
  dragOver.value = false
  fileError.value = null
  importComplete.value = false
  store.clearData()
}

function closeModal() {
  emit('update:modelValue', false)
}

function openFilePicker() {
  fileInput.value?.click()
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  dragOver.value = true
}

function onDragLeave() {
  dragOver.value = false
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  dragOver.value = false

  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    handleFileSelection(files[0])
  }
}

function onFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (files && files.length > 0) {
    handleFileSelection(files[0])
  }
  input.value = ''
}

function handleFileSelection(file: File) {
  fileError.value = null

  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!ACCEPTED_EXTENSIONS.includes(ext)) {
    fileError.value = `지원하지 않는 파일 형식입니다. (${ACCEPTED_EXTENSIONS.join(', ')} 파일만 가능)`
    return
  }

  if (file.type && !ACCEPTED_MIME_TYPES.includes(file.type) && file.type !== 'text/plain') {
    fileError.value = '올바른 CSV/Excel 파일이 아닙니다.'
    return
  }

  if (file.size > 10 * 1024 * 1024) {
    fileError.value = '파일 크기가 10MB를 초과합니다.'
    return
  }

  selectedFile.value = file
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

function downloadTemplate() {
  const headers = store.templateColumns.map((col) => col.label).join(',')
  const exampleRow = store.templateColumns.map((col) => col.example).join(',')
  const csvContent = `${headers}\n${exampleRow}\n`

  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'ongo_schedule_template.csv'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

async function goNext() {
  if (currentStep.value === 1 && selectedFile.value) {
    try {
      await store.parseFile(selectedFile.value)
      currentStep.value = 2
    } catch {
      fileError.value = store.parseError || '파일 파싱 중 오류가 발생했습니다.'
    }
  } else if (currentStep.value === 2) {
    currentStep.value = 3
  }
}

function goPrevious() {
  if (currentStep.value > 1) {
    currentStep.value = currentStep.value - 1
  }
}

async function startImport() {
  try {
    await store.importSchedules()
    importComplete.value = true
    emit('imported')
  } catch {
    // Error handled by store
  }
}

function handleRemoveRow(rowNumber: number) {
  store.removeRow(rowNumber)
}

function handleEditRow(rowNumber: number, field: string, value: string) {
  store.editRow(rowNumber, field, value)
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="csv-import-modal-title"
    >
      <!-- Backdrop -->
      <div class="fixed inset-0 bg-black/50 transition-opacity" aria-hidden="true" @click="closeModal" />

      <!-- Modal -->
      <div
        class="relative flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl dark:bg-gray-800"
        @keydown.escape="closeModal"
      >
        <!-- Header -->
        <div
          class="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-700"
        >
          <div class="flex items-center gap-3">
            <CloudArrowUpIcon class="h-6 w-6 text-primary-500" />
            <div>
              <h2 id="csv-import-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                CSV 일괄 스케줄 가져오기
              </h2>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                단계 {{ currentStep }}/3 - {{ stepTitle }}
              </p>
            </div>
          </div>
          <button
            type="button"
            class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
            aria-label="모달 닫기"
            @click="closeModal"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Step indicators -->
        <div class="border-b border-gray-200 bg-gray-50 px-6 py-3 dark:border-gray-700 dark:bg-gray-800/50">
          <div class="flex items-center gap-2">
            <div
              v-for="step in 3"
              :key="step"
              class="flex items-center"
            >
              <div
                class="flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold transition-colors"
                :class="[
                  step < currentStep
                    ? 'bg-primary-500 text-white'
                    : step === currentStep
                      ? 'bg-primary-100 text-primary-700 ring-2 ring-primary-500 dark:bg-primary-900/30 dark:text-primary-400'
                      : 'bg-gray-200 text-gray-500 dark:bg-gray-700 dark:text-gray-400',
                ]"
              >
                <CheckCircleIcon v-if="step < currentStep" class="h-4 w-4" />
                <span v-else>{{ step }}</span>
              </div>
              <div
                v-if="step < 3"
                class="mx-2 h-px w-8 transition-colors"
                :class="[
                  step < currentStep
                    ? 'bg-primary-500'
                    : 'bg-gray-300 dark:bg-gray-600',
                ]"
              />
            </div>
          </div>
        </div>

        <!-- Content -->
        <div class="flex-1 overflow-y-auto px-6 py-5">
          <!-- Step 1: File Upload -->
          <div v-if="currentStep === 1" class="space-y-4">
            <!-- Template info -->
            <CsvTemplateInfo />

            <!-- Drop zone -->
            <div
              class="relative cursor-pointer rounded-xl border-2 border-dashed transition-all duration-300"
              :class="[
                dragOver
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                  : selectedFile
                    ? 'border-green-400 bg-green-50 dark:border-green-600 dark:bg-green-900/10'
                    : 'border-gray-300 bg-gray-50 hover:border-gray-400 dark:border-gray-600 dark:bg-gray-800/50 dark:hover:border-gray-500',
              ]"
              @dragover.prevent="onDragOver"
              @dragleave.prevent="onDragLeave"
              @drop.prevent="onDrop"
              @click="openFilePicker"
            >
              <div class="flex flex-col items-center justify-center py-12">
                <ArrowUpTrayIcon
                  v-if="!selectedFile"
                  class="mb-3 h-10 w-10"
                  :class="[
                    dragOver
                      ? 'text-primary-500'
                      : 'text-gray-400 dark:text-gray-500',
                  ]"
                />
                <DocumentTextIcon
                  v-else
                  class="mb-3 h-10 w-10 text-green-500"
                />

                <p
                  class="mb-1 text-sm font-medium"
                  :class="[
                    dragOver
                      ? 'text-primary-600 dark:text-primary-400'
                      : selectedFile
                        ? 'text-green-600 dark:text-green-400'
                        : 'text-gray-700 dark:text-gray-300',
                  ]"
                >
                  {{
                    dragOver
                      ? '여기에 놓으세요!'
                      : selectedFile
                        ? selectedFile.name
                        : '드래그 & 드롭으로 파일 업로드'
                  }}
                </p>

                <p
                  v-if="!selectedFile"
                  class="text-xs text-gray-500 dark:text-gray-400"
                >
                  또는 클릭하여 파일 선택 | CSV, XLSX, XLS | 최대 10MB
                </p>

                <p
                  v-if="selectedFile"
                  class="text-xs text-gray-500 dark:text-gray-400"
                >
                  {{ formatFileSize(selectedFile.size) }} | 클릭하여 다른 파일 선택
                </p>
              </div>

              <input
                ref="fileInput"
                type="file"
                class="hidden"
                :accept="ACCEPTED_EXTENSIONS.join(',')"
                @change="onFileSelect"
              />
            </div>

            <!-- File error -->
            <div
              v-if="fileError"
              class="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 dark:bg-red-900/20"
            >
              <XCircleIcon class="h-5 w-5 flex-shrink-0 text-red-500" />
              <p class="text-sm text-red-600 dark:text-red-400">{{ fileError }}</p>
            </div>

            <!-- Template download link -->
            <div class="flex items-center justify-center">
              <button
                type="button"
                class="inline-flex items-center gap-1.5 text-sm text-primary-600 transition-colors hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
                @click.stop="downloadTemplate"
              >
                <ArrowDownTrayIcon class="h-4 w-4" />
                CSV 템플릿 다운로드
              </button>
            </div>
          </div>

          <!-- Step 2: Preview & Validation -->
          <div v-if="currentStep === 2 && store.parsedData" class="space-y-4">
            <!-- Stats summary -->
            <div class="flex flex-wrap items-center gap-4">
              <div
                class="flex items-center gap-2 rounded-lg bg-gray-100 px-3 py-2 dark:bg-gray-700"
              >
                <span class="text-sm text-gray-600 dark:text-gray-400">
                  총 <span class="font-semibold text-gray-900 dark:text-gray-100">{{ store.parsedData.totalRows }}</span>행 중
                </span>
              </div>

              <div class="flex items-center gap-1.5">
                <CheckCircleIcon class="h-4 w-4 text-green-500" />
                <span class="text-sm text-green-600 dark:text-green-400">
                  {{ store.parsedData.validRows }}개 유효
                </span>
              </div>

              <div v-if="store.parsedData.warningRows > 0" class="flex items-center gap-1.5">
                <ExclamationTriangleIcon class="h-4 w-4 text-yellow-500" />
                <span class="text-sm text-yellow-600 dark:text-yellow-400">
                  {{ store.parsedData.warningRows }}개 경고
                </span>
              </div>

              <div v-if="store.parsedData.errorRows > 0" class="flex items-center gap-1.5">
                <XCircleIcon class="h-4 w-4 text-red-500" />
                <span class="text-sm text-red-600 dark:text-red-400">
                  {{ store.parsedData.errorRows }}개 오류
                </span>
              </div>
            </div>

            <!-- Error hint -->
            <div
              v-if="store.parsedData.errorRows > 0"
              class="rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3 dark:border-yellow-800 dark:bg-yellow-900/20"
            >
              <p class="text-sm text-yellow-700 dark:text-yellow-400">
                오류가 있는 행은 가져오기에서 제외됩니다. 수정 버튼을 눌러 인라인으로 수정하거나, 삭제 버튼으로 제거할 수 있습니다.
              </p>
            </div>

            <!-- Preview table -->
            <CsvPreviewTable
              :rows="store.parsedData.rows"
              @remove-row="handleRemoveRow"
              @edit-row="handleEditRow"
            />
          </div>

          <!-- Step 3: Import -->
          <div v-if="currentStep === 3" class="space-y-6">
            <!-- Import not started yet -->
            <div v-if="!store.importing && !importComplete">
              <div class="rounded-lg border border-gray-200 bg-gray-50 p-6 dark:border-gray-700 dark:bg-gray-800/50">
                <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
                  가져오기 요약
                </h3>

                <div class="space-y-3">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-gray-600 dark:text-gray-400">가져올 스케줄 수</span>
                    <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                      {{ store.importableRows.length }}건
                    </span>
                  </div>

                  <div
                    v-if="store.parsedData && store.parsedData.errorRows > 0"
                    class="flex items-center justify-between"
                  >
                    <span class="text-sm text-gray-600 dark:text-gray-400">제외된 오류 행</span>
                    <span class="text-sm font-semibold text-red-600 dark:text-red-400">
                      {{ store.parsedData.errorRows }}건
                    </span>
                  </div>

                  <div
                    v-if="store.parsedData && store.parsedData.warningRows > 0"
                    class="flex items-center justify-between"
                  >
                    <span class="text-sm text-gray-600 dark:text-gray-400">경고 포함</span>
                    <span class="text-sm font-semibold text-yellow-600 dark:text-yellow-400">
                      {{ store.parsedData.warningRows }}건
                    </span>
                  </div>

                  <hr class="border-gray-200 dark:border-gray-700" />

                  <div class="flex items-center justify-between">
                    <span class="text-sm font-medium text-gray-700 dark:text-gray-300">총 처리 예정</span>
                    <span class="text-base font-bold text-primary-600 dark:text-primary-400">
                      {{ store.importableRows.length }}건
                    </span>
                  </div>
                </div>
              </div>

              <div
                v-if="store.parsedData && store.parsedData.warningRows > 0"
                class="mt-4 flex items-start gap-2 rounded-lg bg-yellow-50 px-4 py-3 dark:bg-yellow-900/20"
              >
                <ExclamationTriangleIcon class="mt-0.5 h-5 w-5 flex-shrink-0 text-yellow-500" />
                <p class="text-sm text-yellow-700 dark:text-yellow-400">
                  경고가 포함된 행도 함께 가져옵니다. 문제가 없는지 확인해 주세요.
                </p>
              </div>
            </div>

            <!-- Importing in progress -->
            <div v-if="store.importing" class="flex flex-col items-center py-8">
              <CloudArrowUpIcon class="mb-4 h-12 w-12 animate-pulse text-primary-500" />
              <p class="mb-4 text-sm font-medium text-gray-700 dark:text-gray-300">
                스케줄을 가져오는 중...
              </p>

              <!-- Progress bar -->
              <div class="w-full max-w-md">
                <div class="mb-2 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                  <span>진행 중</span>
                  <span>{{ store.importProgress }}%</span>
                </div>
                <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                  <div
                    class="h-full rounded-full bg-primary-500 transition-all duration-300"
                    :style="{ width: store.importProgress + '%' }"
                  />
                </div>
              </div>
            </div>

            <!-- Import complete -->
            <div v-if="importComplete && !store.importing" class="flex flex-col items-center py-8">
              <div class="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
                <CheckCircleIcon class="h-10 w-10 text-green-500" />
              </div>
              <h3 class="mb-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
                가져오기 완료
              </h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">
                {{ store.importableRows.length }}건의 스케줄이 성공적으로 등록되었습니다.
              </p>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div
          class="flex items-center justify-between border-t border-gray-200 bg-gray-50 px-6 py-4 dark:border-gray-700 dark:bg-gray-800/50"
        >
          <button
            type="button"
            class="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
            @click="closeModal"
          >
            {{ importComplete ? '닫기' : '취소' }}
          </button>

          <div class="flex items-center gap-3">
            <!-- Previous button -->
            <button
              v-if="currentStep > 1 && !store.importing && !importComplete"
              type="button"
              class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
              @click="goPrevious"
            >
              <ArrowLeftIcon class="h-4 w-4" />
              이전
            </button>

            <!-- Next button (step 1 and 2) -->
            <button
              v-if="currentStep < 3 && !store.importing"
              type="button"
              class="btn-primary gap-1.5"
              :disabled="!canGoNext"
              @click="goNext"
            >
              다음
              <ArrowRightIcon class="h-4 w-4" />
            </button>

            <!-- Import button (step 3, before import) -->
            <button
              v-if="currentStep === 3 && !store.importing && !importComplete"
              type="button"
              class="btn-primary gap-1.5 px-5"
              @click="startImport"
            >
              <CloudArrowUpIcon class="h-4 w-4" />
              가져오기 시작
            </button>

            <!-- Done button (after import) -->
            <button
              v-if="importComplete"
              type="button"
              class="inline-flex items-center gap-1.5 rounded-lg bg-green-600 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-600"
              @click="closeModal"
            >
              <CheckCircleIcon class="h-4 w-4" />
              완료
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
