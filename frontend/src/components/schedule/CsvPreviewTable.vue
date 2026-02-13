<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  TrashIcon,
  PencilSquareIcon,
} from '@heroicons/vue/24/outline'
import type { CsvScheduleRow, CsvValidationStatus } from '@/types/csvImport'

const props = defineProps<{
  rows: CsvScheduleRow[]
}>()

const emit = defineEmits<{
  removeRow: [rowNumber: number]
  editRow: [rowNumber: number, field: string, value: string]
}>()

type SortOrder = 'default' | 'errors-first' | 'valid-first'

const sortOrder = ref<SortOrder>('errors-first')
const editingCell = ref<{ rowNumber: number; field: string } | null>(null)
const editValue = ref('')
const hoveredRow = ref<number | null>(null)

const sortedRows = computed(() => {
  const rows = [...props.rows]

  if (sortOrder.value === 'errors-first') {
    const statusPriority: Record<CsvValidationStatus, number> = {
      error: 0,
      warning: 1,
      valid: 2,
    }
    rows.sort((a, b) => statusPriority[a.status] - statusPriority[b.status])
  } else if (sortOrder.value === 'valid-first') {
    const statusPriority: Record<CsvValidationStatus, number> = {
      valid: 0,
      warning: 1,
      error: 2,
    }
    rows.sort((a, b) => statusPriority[a.status] - statusPriority[b.status])
  }

  return rows
})

const platformLabels: Record<string, string> = {
  YT: 'YouTube',
  TT: 'TikTok',
  IG: 'Instagram',
  NV: 'Naver Clip',
}

const visibilityLabels: Record<string, string> = {
  public: '공개',
  private: '비공개',
  unlisted: '일부공개',
}

function getStatusIcon(status: CsvValidationStatus) {
  switch (status) {
    case 'valid':
      return CheckCircleIcon
    case 'warning':
      return ExclamationTriangleIcon
    case 'error':
      return XCircleIcon
  }
}

function getStatusColor(status: CsvValidationStatus): string {
  switch (status) {
    case 'valid':
      return 'text-green-500'
    case 'warning':
      return 'text-yellow-500'
    case 'error':
      return 'text-red-500'
  }
}

function getRowBgClass(status: CsvValidationStatus): string {
  switch (status) {
    case 'valid':
      return 'bg-green-50/50 dark:bg-green-900/10'
    case 'warning':
      return 'bg-yellow-50/50 dark:bg-yellow-900/10'
    case 'error':
      return 'bg-red-50/50 dark:bg-red-900/10'
  }
}

function getTooltipText(row: CsvScheduleRow): string {
  const messages: string[] = []
  if (row.errors.length > 0) {
    messages.push(...row.errors.map((e) => `[오류] ${e}`))
  }
  if (row.warnings.length > 0) {
    messages.push(...row.warnings.map((w) => `[경고] ${w}`))
  }
  return messages.join('\n')
}

function formatPlatforms(platforms: string[]): string {
  return platforms.map((p) => platformLabels[p] || p).join(', ')
}

function formatVisibility(visibility: string): string {
  return visibilityLabels[visibility] || visibility
}

function startEdit(rowNumber: number, field: string, currentValue: string) {
  editingCell.value = { rowNumber, field }
  editValue.value = currentValue
}

function saveEdit() {
  if (editingCell.value) {
    emit('editRow', editingCell.value.rowNumber, editingCell.value.field, editValue.value)
    editingCell.value = null
    editValue.value = ''
  }
}

function cancelEdit() {
  editingCell.value = null
  editValue.value = ''
}

function isEditing(rowNumber: number, field: string): boolean {
  return editingCell.value?.rowNumber === rowNumber && editingCell.value?.field === field
}

function cycleSortOrder() {
  const orders: SortOrder[] = ['default', 'errors-first', 'valid-first']
  const currentIndex = orders.indexOf(sortOrder.value)
  sortOrder.value = orders[(currentIndex + 1) % orders.length]
}

function getSortLabel(): string {
  switch (sortOrder.value) {
    case 'default':
      return '기본 순서'
    case 'errors-first':
      return '오류 우선'
    case 'valid-first':
      return '유효 우선'
  }
}

function getCellValue(row: CsvScheduleRow, field: string): string {
  switch (field) {
    case 'title':
      return row.title
    case 'description':
      return row.description
    case 'tags':
      return row.tags.join(';')
    case 'platforms':
      return row.platforms.join(';')
    case 'scheduledAt':
      return row.scheduledAt
    case 'visibility':
      return row.visibility
    default:
      return ''
  }
}
</script>

<template>
  <div class="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
    <!-- Table toolbar -->
    <div
      class="flex items-center justify-between border-b border-gray-200 bg-gray-50 px-4 py-2 dark:border-gray-700 dark:bg-gray-800"
    >
      <span class="text-xs text-gray-500 dark:text-gray-400">
        총 {{ rows.length }}행
      </span>
      <button
        type="button"
        class="text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
        @click="cycleSortOrder"
      >
        정렬: {{ getSortLabel() }}
      </button>
    </div>

    <!-- Table container -->
    <div class="max-h-[400px] overflow-auto">
      <table class="w-full min-w-[900px] text-sm">
        <thead class="sticky top-0 z-10 bg-white dark:bg-gray-800">
          <tr class="border-b border-gray-200 dark:border-gray-700">
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              #
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              상태
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              제목
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              설명
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              태그
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              플랫폼
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              예약일시
            </th>
            <th
              class="px-3 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              공개설정
            </th>
            <th
              class="px-3 py-2.5 text-right text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              작업
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700/50">
          <tr
            v-for="row in sortedRows"
            :key="row.rowNumber"
            :class="getRowBgClass(row.status)"
            :title="getTooltipText(row)"
            @mouseenter="hoveredRow = row.rowNumber"
            @mouseleave="hoveredRow = null"
          >
            <!-- Row number -->
            <td class="whitespace-nowrap px-3 py-2 text-gray-500 dark:text-gray-400">
              {{ row.rowNumber }}
            </td>

            <!-- Status icon -->
            <td class="px-3 py-2">
              <div class="group relative">
                <component
                  :is="getStatusIcon(row.status)"
                  class="h-5 w-5"
                  :class="getStatusColor(row.status)"
                />
                <!-- Tooltip -->
                <div
                  v-if="(row.errors.length > 0 || row.warnings.length > 0) && hoveredRow === row.rowNumber"
                  class="absolute left-6 top-0 z-20 w-64 rounded-lg border border-gray-200 bg-white p-3 shadow-lg dark:border-gray-600 dark:bg-gray-700"
                >
                  <div v-if="row.errors.length > 0" class="mb-2">
                    <p class="mb-1 text-xs font-semibold text-red-600 dark:text-red-400">오류:</p>
                    <ul class="space-y-0.5">
                      <li
                        v-for="(error, idx) in row.errors"
                        :key="'e' + idx"
                        class="text-xs text-red-600 dark:text-red-400"
                      >
                        {{ error }}
                      </li>
                    </ul>
                  </div>
                  <div v-if="row.warnings.length > 0">
                    <p class="mb-1 text-xs font-semibold text-yellow-600 dark:text-yellow-400">경고:</p>
                    <ul class="space-y-0.5">
                      <li
                        v-for="(warning, idx) in row.warnings"
                        :key="'w' + idx"
                        class="text-xs text-yellow-600 dark:text-yellow-400"
                      >
                        {{ warning }}
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </td>

            <!-- Title -->
            <td class="max-w-[160px] px-3 py-2">
              <div v-if="isEditing(row.rowNumber, 'title')">
                <input
                  v-model="editValue"
                  type="text"
                  class="w-full rounded border border-primary-300 bg-white px-2 py-1 text-xs dark:border-primary-600 dark:bg-gray-700 dark:text-gray-100"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  @blur="saveEdit"
                />
              </div>
              <span
                v-else
                class="block truncate text-gray-900 dark:text-gray-100"
                :title="row.title"
              >
                {{ row.title || '-' }}
              </span>
            </td>

            <!-- Description -->
            <td class="max-w-[140px] px-3 py-2">
              <div v-if="isEditing(row.rowNumber, 'description')">
                <input
                  v-model="editValue"
                  type="text"
                  class="w-full rounded border border-primary-300 bg-white px-2 py-1 text-xs dark:border-primary-600 dark:bg-gray-700 dark:text-gray-100"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  @blur="saveEdit"
                />
              </div>
              <span
                v-else
                class="block truncate text-gray-600 dark:text-gray-400"
                :title="row.description"
              >
                {{ row.description || '-' }}
              </span>
            </td>

            <!-- Tags -->
            <td class="max-w-[120px] px-3 py-2">
              <div v-if="isEditing(row.rowNumber, 'tags')">
                <input
                  v-model="editValue"
                  type="text"
                  class="w-full rounded border border-primary-300 bg-white px-2 py-1 text-xs dark:border-primary-600 dark:bg-gray-700 dark:text-gray-100"
                  placeholder="태그1;태그2"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  @blur="saveEdit"
                />
              </div>
              <div v-else class="flex flex-wrap gap-1">
                <span
                  v-for="(tag, idx) in row.tags.slice(0, 3)"
                  :key="idx"
                  class="inline-block rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                >
                  {{ tag }}
                </span>
                <span
                  v-if="row.tags.length > 3"
                  class="text-xs text-gray-400 dark:text-gray-500"
                >
                  +{{ row.tags.length - 3 }}
                </span>
                <span v-if="row.tags.length === 0" class="text-xs text-gray-400 dark:text-gray-500">
                  -
                </span>
              </div>
            </td>

            <!-- Platforms -->
            <td class="px-3 py-2">
              <div v-if="isEditing(row.rowNumber, 'platforms')">
                <input
                  v-model="editValue"
                  type="text"
                  class="w-full rounded border border-primary-300 bg-white px-2 py-1 text-xs dark:border-primary-600 dark:bg-gray-700 dark:text-gray-100"
                  placeholder="YT;TT;IG;NV"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  @blur="saveEdit"
                />
              </div>
              <span v-else class="text-xs text-gray-700 dark:text-gray-300">
                {{ formatPlatforms(row.platforms) || '-' }}
              </span>
            </td>

            <!-- Scheduled At -->
            <td class="whitespace-nowrap px-3 py-2">
              <div v-if="isEditing(row.rowNumber, 'scheduledAt')">
                <input
                  v-model="editValue"
                  type="text"
                  class="w-full rounded border border-primary-300 bg-white px-2 py-1 text-xs dark:border-primary-600 dark:bg-gray-700 dark:text-gray-100"
                  placeholder="YYYY-MM-DD HH:mm"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  @blur="saveEdit"
                />
              </div>
              <span v-else class="text-xs text-gray-700 dark:text-gray-300">
                {{ row.scheduledAt || '-' }}
              </span>
            </td>

            <!-- Visibility -->
            <td class="px-3 py-2">
              <div v-if="isEditing(row.rowNumber, 'visibility')">
                <select
                  v-model="editValue"
                  class="w-full rounded border border-primary-300 bg-white px-2 py-1 text-xs dark:border-primary-600 dark:bg-gray-700 dark:text-gray-100"
                  @change="saveEdit"
                  @blur="saveEdit"
                >
                  <option value="public">공개</option>
                  <option value="private">비공개</option>
                  <option value="unlisted">일부공개</option>
                </select>
              </div>
              <span v-else class="text-xs text-gray-700 dark:text-gray-300">
                {{ formatVisibility(row.visibility) || '-' }}
              </span>
            </td>

            <!-- Actions -->
            <td class="whitespace-nowrap px-3 py-2 text-right">
              <div class="flex items-center justify-end gap-1">
                <button
                  v-if="!editingCell"
                  type="button"
                  class="rounded p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-primary-600 dark:hover:bg-gray-700 dark:hover:text-primary-400"
                  title="행 수정"
                  @click="startEdit(row.rowNumber, 'title', getCellValue(row, 'title'))"
                >
                  <PencilSquareIcon class="h-4 w-4" />
                </button>
                <button
                  type="button"
                  class="rounded p-1 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                  title="행 삭제"
                  @click="emit('removeRow', row.rowNumber)"
                >
                  <TrashIcon class="h-4 w-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty state -->
    <div
      v-if="rows.length === 0"
      class="flex flex-col items-center justify-center py-12 text-gray-500 dark:text-gray-400"
    >
      <p class="text-sm">데이터가 없습니다.</p>
    </div>
  </div>
</template>
