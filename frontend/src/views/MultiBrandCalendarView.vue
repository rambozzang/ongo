<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  XMarkIcon,
  CalendarDaysIcon,
  BuildingStorefrontIcon,
  ClockIcon,
  CheckCircleIcon,
  SwatchIcon,
  UserIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import BrandTag from '@/components/multibrandcalendar/BrandTag.vue'
import ScheduleCard from '@/components/multibrandcalendar/ScheduleCard.vue'
import ConflictAlert from '@/components/multibrandcalendar/ConflictAlert.vue'
import { useMultiBrandCalendarStore } from '@/stores/multiBrandCalendar'
import type { BrandColor, BrandScheduleItem, CreateBrandRequest, CreateScheduleRequest } from '@/types/multiBrandCalendar'

const store = useMultiBrandCalendarStore()

const showCreateBrandModal = ref(false)
const showCreateScheduleModal = ref(false)
const selectedBrandFilter = ref<number | null>(null)

/* ---- Brand form ---- */
const brandForm = ref<CreateBrandRequest>({
  name: '',
  color: 'BLUE',
  category: '',
  assignedEditors: [],
})
const editorInput = ref('')

/* ---- Schedule form ---- */
const scheduleForm = ref<CreateScheduleRequest>({
  brandId: 0,
  title: '',
  platform: 'YOUTUBE',
  scheduledAt: '',
  assignedTo: '',
  notes: '',
})

const brandColorOptions: { key: BrandColor; hex: string; label: string }[] = [
  { key: 'RED', hex: '#EF4444', label: '빨강' },
  { key: 'BLUE', hex: '#3B82F6', label: '파랑' },
  { key: 'GREEN', hex: '#10B981', label: '초록' },
  { key: 'PURPLE', hex: '#8B5CF6', label: '보라' },
  { key: 'ORANGE', hex: '#F97316', label: '주황' },
  { key: 'PINK', hex: '#EC4899', label: '분홍' },
  { key: 'TEAL', hex: '#14B8A6', label: '청록' },
  { key: 'YELLOW', hex: '#EAB308', label: '노랑' },
]

const platformOptions = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

onMounted(async () => {
  const now = new Date()
  const startDate = new Date(now.getFullYear(), now.getMonth(), 1).toISOString()
  const endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString()
  await Promise.all([store.fetchBrands(), store.fetchSchedule(startDate, endDate)])
})

/* ---- Computed ---- */
const filteredSchedule = computed(() => {
  let items = store.schedule
  if (selectedBrandFilter.value) {
    items = items.filter((s) => s.brandId === selectedBrandFilter.value)
  }
  return items.sort((a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime())
})

const groupedSchedule = computed(() => {
  const groups: Record<string, BrandScheduleItem[]> = {}
  for (const item of filteredSchedule.value) {
    const dateKey = new Date(item.scheduledAt).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'short',
    })
    if (!groups[dateKey]) groups[dateKey] = []
    groups[dateKey].push(item)
  }
  return groups
})

const summaryStats = computed(() => ({
  totalBrands: store.brands.length,
  activeBrands: store.activeBrands.length,
  scheduledThisWeek: store.totalScheduledThisWeek,
  publishedThisMonth: store.schedule.filter((s) => s.status === 'PUBLISHED').length,
}))

/* ---- Handlers ---- */
function toggleBrandFilter(brandId: number) {
  if (selectedBrandFilter.value === brandId) {
    selectedBrandFilter.value = null
  } else {
    selectedBrandFilter.value = brandId
  }
}

function openCreateBrand() {
  brandForm.value = { name: '', color: 'BLUE', category: '', assignedEditors: [] }
  editorInput.value = ''
  showCreateBrandModal.value = true
}

function openCreateSchedule() {
  const defaultBrandId = store.brands.length > 0 ? store.brands[0].id : 0
  scheduleForm.value = {
    brandId: defaultBrandId,
    title: '',
    platform: 'YOUTUBE',
    scheduledAt: '',
    assignedTo: '',
    notes: '',
  }
  showCreateScheduleModal.value = true
}

async function handleCreateBrand() {
  if (!brandForm.value.name.trim()) return
  await store.createBrand({ ...brandForm.value })
  showCreateBrandModal.value = false
}

async function handleCreateSchedule() {
  if (!scheduleForm.value.title.trim() || !scheduleForm.value.scheduledAt) return
  await store.createScheduleItem({ ...scheduleForm.value })
  showCreateScheduleModal.value = false
}

function handleEditSchedule(id: number) {
  const item = store.schedule.find((s) => s.id === id)
  if (item) {
    scheduleForm.value = {
      brandId: item.brandId,
      title: item.title,
      platform: item.platform,
      scheduledAt: item.scheduledAt.slice(0, 16),
      assignedTo: item.assignedTo ?? '',
      notes: item.notes ?? '',
    }
    showCreateScheduleModal.value = true
  }
}

function handleDeleteSchedule(id: number) {
  if (confirm('이 일정을 삭제하시겠습니까?')) {
    store.deleteScheduleItem(id)
  }
}

function addEditor() {
  const name = editorInput.value.trim()
  if (name && !brandForm.value.assignedEditors?.includes(name)) {
    brandForm.value.assignedEditors = [...(brandForm.value.assignedEditors ?? []), name]
  }
  editorInput.value = ''
}

function removeEditor(editor: string) {
  brandForm.value.assignedEditors = brandForm.value.assignedEditors?.filter((e) => e !== editor)
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <div class="p-2 rounded-lg bg-primary-100 dark:bg-primary-900/30">
            <CalendarDaysIcon class="w-7 h-7 text-primary-600 dark:text-primary-400" />
          </div>
          <div>
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              다중 브랜드 캘린더
            </h1>
            <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
              {{ store.brands.length }}개 브랜드의 일정을 한눈에 관리하세요
            </p>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <button @click="openCreateBrand" class="btn-secondary inline-flex items-center gap-2">
          <PlusIcon class="w-5 h-5" />
          브랜드 추가
        </button>
        <button @click="openCreateSchedule" class="btn-primary inline-flex items-center gap-2">
          <PlusIcon class="w-5 h-5" />
          일정 추가
        </button>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.isLoading" :full-page="true" size="lg" />

    <div v-else class="space-y-6">
      <!-- Summary Cards -->
      <div class="grid grid-cols-2 tablet:grid-cols-4 gap-4">
        <div class="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <BuildingStorefrontIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summaryStats.totalBrands }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">전체 브랜드</p>
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/30">
              <SwatchIcon class="w-5 h-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summaryStats.activeBrands }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">활성 브랜드</p>
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-2 rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <ClockIcon class="w-5 h-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summaryStats.scheduledThisWeek }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">이번 주 예약</p>
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-2 rounded-lg bg-yellow-100 dark:bg-yellow-900/30">
              <CheckCircleIcon class="w-5 h-5 text-yellow-600 dark:text-yellow-400" />
            </div>
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summaryStats.publishedThisMonth }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">이번 달 발행</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Conflict Alerts -->
      <div v-if="store.conflicts.length > 0" class="space-y-3">
        <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">일정 충돌</h2>
        <ConflictAlert
          v-for="conflict in store.conflicts"
          :key="conflict.date"
          :conflict="conflict"
        />
      </div>

      <!-- Brand Filter Bar -->
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300 mr-2">브랜드 필터:</span>
        <BrandTag
          v-for="brand in store.brands"
          :key="brand.id"
          :name="brand.name"
          :color="brand.color"
          :active="selectedBrandFilter === brand.id"
          @click="toggleBrandFilter(brand.id)"
        />
        <button
          v-if="selectedBrandFilter"
          @click="selectedBrandFilter = null"
          class="text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 underline ml-2"
        >
          필터 초기화
        </button>
      </div>

      <!-- Schedule List (grouped by date) -->
      <div v-if="Object.keys(groupedSchedule).length > 0" class="space-y-6">
        <div v-for="(items, dateLabel) in groupedSchedule" :key="dateLabel">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
            <CalendarDaysIcon class="w-4 h-4 text-gray-400" />
            {{ dateLabel }}
            <span class="text-xs font-normal text-gray-500 dark:text-gray-400">({{ items.length }}개)</span>
          </h3>
          <div class="space-y-3">
            <ScheduleCard
              v-for="item in items"
              :key="item.id"
              :item="item"
              @edit="handleEditSchedule"
              @delete="handleDeleteSchedule"
            />
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
      >
        <CalendarDaysIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          예약된 일정이 없습니다
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
          브랜드를 추가하고 일정을 등록하여 멀티 브랜드 캘린더를 시작하세요.
        </p>
        <button @click="openCreateSchedule" class="btn-primary inline-flex items-center gap-2">
          <PlusIcon class="w-5 h-5" />
          첫 일정 등록
        </button>
      </div>
    </div>

    <!-- ============ Create Brand Modal ============ -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showCreateBrandModal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showCreateBrandModal = false"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="showCreateBrandModal"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Modal Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  새 브랜드 추가
                </h2>
                <button
                  @click="showCreateBrandModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Modal Body -->
              <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
                <!-- Name -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    브랜드 이름
                  </label>
                  <input
                    v-model="brandForm.name"
                    type="text"
                    placeholder="예: 뷰티 브랜드 A"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Color picker -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    브랜드 색상
                  </label>
                  <div class="flex flex-wrap gap-3">
                    <button
                      v-for="option in brandColorOptions"
                      :key="option.key"
                      @click="brandForm.color = option.key"
                      :class="[
                        'w-10 h-10 rounded-full border-2 transition-all flex items-center justify-center',
                        brandForm.color === option.key
                          ? 'border-gray-900 dark:border-white scale-110 shadow-lg'
                          : 'border-transparent hover:border-gray-300 dark:hover:border-gray-600',
                      ]"
                      :style="{ backgroundColor: option.hex }"
                      :title="option.label"
                    >
                      <span
                        v-if="brandForm.color === option.key"
                        class="text-white text-sm font-bold"
                      >
                        &#10003;
                      </span>
                    </button>
                  </div>
                </div>

                <!-- Category -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    카테고리
                  </label>
                  <input
                    v-model="brandForm.category"
                    type="text"
                    placeholder="예: 뷰티, 테크, 음식"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Assigned Editors -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    담당 에디터
                  </label>
                  <div class="flex items-center gap-2">
                    <div class="relative flex-1">
                      <UserIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                      <input
                        v-model="editorInput"
                        type="text"
                        placeholder="에디터 이름 입력"
                        class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 pl-9 pr-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                        @keydown.enter.prevent="addEditor"
                      />
                    </div>
                    <button type="button" @click="addEditor" class="btn-secondary px-3 py-2 text-sm">
                      추가
                    </button>
                  </div>
                  <div v-if="brandForm.assignedEditors && brandForm.assignedEditors.length > 0" class="flex flex-wrap gap-1.5 mt-2">
                    <span
                      v-for="editor in brandForm.assignedEditors"
                      :key="editor"
                      class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                    >
                      {{ editor }}
                      <button @click="removeEditor(editor)" class="hover:text-primary-900 dark:hover:text-primary-100">
                        <XMarkIcon class="w-3 h-3" />
                      </button>
                    </span>
                  </div>
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button @click="showCreateBrandModal = false" class="btn-secondary">
                  취소
                </button>
                <button
                  @click="handleCreateBrand"
                  :disabled="!brandForm.name.trim()"
                  class="btn-primary"
                >
                  브랜드 추가
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>

    <!-- ============ Create Schedule Modal ============ -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showCreateScheduleModal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showCreateScheduleModal = false"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="showCreateScheduleModal"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Modal Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  새 일정 추가
                </h2>
                <button
                  @click="showCreateScheduleModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Modal Body -->
              <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
                <!-- Brand select -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    브랜드
                  </label>
                  <select
                    v-model="scheduleForm.brandId"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option v-for="brand in store.brands" :key="brand.id" :value="brand.id">
                      {{ brand.name }}
                    </option>
                  </select>
                </div>

                <!-- Title -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    콘텐츠 제목
                  </label>
                  <input
                    v-model="scheduleForm.title"
                    type="text"
                    placeholder="예: 봄 신상 메이크업 리뷰"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Platform -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    플랫폼
                  </label>
                  <select
                    v-model="scheduleForm.platform"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option v-for="platform in platformOptions" :key="platform.value" :value="platform.value">
                      {{ platform.label }}
                    </option>
                  </select>
                </div>

                <!-- Date / Time -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    예약 날짜 / 시간
                  </label>
                  <input
                    v-model="scheduleForm.scheduledAt"
                    type="datetime-local"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Assigned Editor -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    담당 에디터
                  </label>
                  <input
                    v-model="scheduleForm.assignedTo"
                    type="text"
                    placeholder="에디터 이름 (선택)"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Notes -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    메모
                  </label>
                  <textarea
                    v-model="scheduleForm.notes"
                    rows="3"
                    placeholder="추가 메모 (선택)"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-none"
                  ></textarea>
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button @click="showCreateScheduleModal = false" class="btn-secondary">
                  취소
                </button>
                <button
                  @click="handleCreateSchedule"
                  :disabled="!scheduleForm.title.trim() || !scheduleForm.scheduledAt"
                  class="btn-primary"
                >
                  일정 추가
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
