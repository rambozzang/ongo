<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <PageHeader :title="$t('audience.title')" :description="$t('audience.description')" />

    <PageGuide :title="$t('audience.pageGuideTitle')" :items="($tm('audience.pageGuide') as string[])" />

    <div v-if="store.profilesError || store.segmentsError" class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong" role="alert">
      <span class="min-w-0 flex-1">{{ store.profilesError || store.segmentsError }}</span>
      <button v-if="store.profilesError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="store.loading" @click="loadProfiles">프로필 다시 시도</button>
      <button v-if="store.segmentsError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="store.loading" @click="loadSegments">세그먼트 다시 시도</button>
    </div>

    <!-- 탭 -->
    <OTabs v-model="activeTab" :tabs="tabs" class="mb-1" />

    <!-- 팬 프로필 탭 -->
    <!-- NOTE: 프로필은 서버 정렬 + 서버 페이지네이션(20건씩)이라 클라이언트 검색·선택 패턴을
         적용하지 않는다. 현재 페이지만 훑는 검색은 "없음"을 잘못 알려주기 때문이다. -->
    <div v-if="activeTab === 'profiles'">
      <div class="flex flex-wrap items-center gap-3">
        <select v-model="sortBy" class="input-field w-full min-w-[8.5rem] sm:w-auto" :aria-label="$t('list.sortLabel')" @change="loadProfiles">
          <option value="engagement_score">{{ $t('audience.sort.engagementScore') }}</option>
          <option value="total_interactions">{{ $t('audience.sort.totalInteractions') }}</option>
          <option value="last_seen_at">{{ $t('audience.sort.lastSeenAt') }}</option>
        </select>
      </div>

      <AsyncState
        :loading="store.loading && store.profiles.length === 0"
        :empty="store.profiles.length === 0"
        skeleton="table"
        :skeleton-count="6"
        :empty-icon="UsersIcon"
        :empty-title="$t('audience.emptyProfiles')"
        :empty-description="$t('audience.emptyProfilesDescription')"
        :retryable="false"
      >
        <SectionCard :title="$t('audience.tabs.profiles')" :meta="String(store.totalProfiles)" body-class="overflow-x-auto !p-0">
          <table class="w-full text-body">
            <thead class="bg-surface-muted">
              <tr>
                <th class="whitespace-nowrap px-4 py-3 text-left text-body-xs font-semibold uppercase tracking-wider text-content-tertiary">{{ $t('audience.table.profile') }}</th>
                <th class="whitespace-nowrap px-4 py-3 text-left text-body-xs font-semibold uppercase tracking-wider text-content-tertiary">{{ $t('audience.table.platform') }}</th>
                <th class="whitespace-nowrap px-4 py-3 text-left text-body-xs font-semibold uppercase tracking-wider text-content-tertiary">{{ $t('audience.table.engagement') }}</th>
                <th class="whitespace-nowrap px-4 py-3 text-left text-body-xs font-semibold uppercase tracking-wider text-content-tertiary">{{ $t('audience.table.tags') }}</th>
                <th class="whitespace-nowrap px-4 py-3 text-right text-body-xs font-semibold uppercase tracking-wider text-content-tertiary">{{ $t('audience.table.interactions') }}</th>
                <th class="whitespace-nowrap px-4 py-3 text-right text-body-xs font-semibold uppercase tracking-wider text-content-tertiary">{{ $t('audience.table.lastActivity') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
              <tr v-for="profile in store.profiles" :key="profile.id" class="border-line-row transition-colors hover:bg-surface-muted">
                <td class="px-4 py-3">
                  <div class="flex items-center gap-3">
                    <img
                      v-if="profile.avatarUrl"
                      :src="profile.avatarUrl"
                      :alt="profile.authorName"
                      class="w-8 h-8 rounded-full object-cover"
                    />
                    <div
                      v-else
                      class="flex h-8 w-8 items-center justify-center rounded-full bg-accent-subtle text-body-xs font-bold text-accent"
                    >
                      {{ profile.authorName.charAt(0) }}
                    </div>
                    <span class="font-medium text-content">{{ profile.authorName }}</span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <PlatformChip v-if="platformCode(profile.platform)" :platform="platformCode(profile.platform)!" size="sm" />
                  <span v-else class="text-body-xs text-content-tertiary">{{ profile.platform }}</span>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <div class="h-2 w-16 overflow-hidden rounded-full bg-surface-muted">
                      <div
                        class="h-full rounded-full"
                        :class="scoreColor(profile.engagementScore)"
                        :style="{ width: `${Math.min(profile.engagementScore, 100)}%` }"
                      />
                    </div>
                    <span class="font-mono text-body-xs text-content-secondary">{{ profile.engagementScore }}</span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <div class="flex flex-wrap gap-1">
                    <span
                      v-for="tag in profile.tags"
                      :key="tag"
                      class="rounded bg-muted-subtle px-2 py-0.5 text-body-xs text-muted-strong"
                    >
                      {{ tag }}
                    </span>
                  </div>
                </td>
                <td class="px-4 py-3 text-right font-mono text-content-secondary">{{ profile.totalInteractions.toLocaleString() }}</td>
                <td class="px-4 py-3 text-right text-body-xs text-content-tertiary">{{ formatDate(profile.lastSeenAt) }}</td>
              </tr>
            </tbody>
          </table>
        </SectionCard>

        <div v-if="store.totalProfiles > 20" class="flex justify-center mt-4">
          <div class="flex gap-2">
            <button
              :disabled="currentPage === 0"
              class="btn-secondary text-body"
              @click="changePage(currentPage - 1)"
            >
              {{ $t('audience.pagination.previous') }}
            </button>
              <span class="px-3 py-1.5 text-body text-content-tertiary">
              {{ currentPage + 1 }} / {{ Math.ceil(store.totalProfiles / 20) }}
            </span>
            <button
              :disabled="(currentPage + 1) * 20 >= store.totalProfiles"
              class="btn-secondary text-body"
              @click="changePage(currentPage + 1)"
            >
              {{ $t('audience.pagination.next') }}
            </button>
          </div>
        </div>
      </AsyncState>
    </div>

    <!-- 세그먼트 탭 -->
    <div v-if="activeTab === 'segments'">
      <!-- 검색 · 정렬 · 일괄 작업 -->
      <ListToolbar
        v-if="!isSourceEmpty"
        v-model="query"
        v-model:sort-key="sortKey"
        v-model:sort-dir="sortDir"
        :sort-options="sortOptions"
        :selected-count="selectedCount"
        :total-count="visibleCount"
        :search-placeholder="$t('audience.segment.searchPlaceholder')"
        :search-label="$t('audience.segment.searchLabel')"
        @clear-selection="clearSelection"
      >
        <template #actions>
          <button
            class="btn-primary inline-flex items-center gap-2"
            @click="showSegmentModal = true"
          >
            <PlusIcon class="h-5 w-5" />
            {{ $t('audience.segment.add') }}
          </button>
        </template>

        <template #bulk-actions>
          <button
            type="button"
            class="btn-danger inline-flex items-center gap-1.5"
            :disabled="bulkDeleting"
            @click="showBulkDeleteModal = true"
          >
            <TrashIcon class="h-4 w-4" aria-hidden="true" />
            {{ $t('list.bulkDelete') }}
          </button>
        </template>
      </ListToolbar>

      <!-- 전체 선택 -->
      <div v-if="!isSourceEmpty && visibleCount > 0" class="mb-3 flex items-center gap-2">
        <input
          id="segments-select-all"
          type="checkbox"
          :class="CHECKBOX_CLASS"
          :checked="allSelected"
          :indeterminate="someSelected"
          @change="toggleAll"
        />
        <label for="segments-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
          {{ $t('list.selectAll', { count: visibleCount }) }}
        </label>
      </div>

      <AsyncState
        :loading="store.loading && isSourceEmpty"
        :empty="isSourceEmpty"
        skeleton="card"
        :skeleton-count="3"
        :empty-icon="UserGroupIcon"
        :empty-title="$t('audience.segment.emptyTitle')"
        :empty-description="$t('audience.segment.emptyDescription')"
        :empty-action-label="$t('audience.segment.add')"
        :retryable="false"
        @empty-action="showSegmentModal = true"
      >
        <!-- 검색 결과만 비었을 때 -->
        <EmptyState
          v-if="isResultEmpty"
          :icon="MagnifyingGlassIcon"
          :title="$t('list.noResultsTitle')"
          :description="$t('list.noResultsDescription')"
          :action-label="$t('list.resetFilters')"
          @action="resetFilters"
        />

        <div v-else class="page-grid page-grid--cards">
          <div
            v-for="segment in filtered"
            :key="segment.id"
            class="flex items-start gap-2"
          >
            <input
              type="checkbox"
              :class="[CHECKBOX_CLASS, 'mt-5']"
              :checked="isSelected(segment.id)"
              :aria-label="$t('list.selectItem', { name: segment.name })"
              @change="toggle(segment.id)"
            />
            <div class="min-w-0 flex-1 rounded-[11px] border border-line bg-surface-card p-4">
              <div class="flex items-start justify-between mb-2">
                <h3 class="font-semibold text-content">{{ segment.name }}</h3>
                <button
                  class="text-gray-400 hover:text-error-strong transition-colors"
                  :aria-label="$t('action.delete')"
                  @click="handleDeleteSegment(segment.id)"
                >
                  <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <p v-if="segment.description" class="mb-3 text-body text-content-tertiary">{{ segment.description }}</p>
              <div class="flex items-center justify-between text-body">
                <span class="text-content-secondary">
                  <span class="font-mono font-medium text-accent">{{ segment.memberCount.toLocaleString() }}</span>{{ $t('audience.members') }}
                </span>
                <span v-if="segment.autoUpdate" class="px-2 py-0.5 bg-success-subtle text-success-strong rounded text-body-xs">
                  {{ $t('audience.autoUpdate') }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </AsyncState>
    </div>

    <!-- 세그먼트 추가 모달 -->
    <BaseModal v-model="showSegmentModal" :title="$t('audience.segment.modal.title')">
      <form @submit.prevent="handleCreateSegment">
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.name') }}</label>
            <input
              v-model="segmentForm.name"
              type="text"
              required
              class="input-field"
              :placeholder="$t('audience.segment.modal.namePlaceholder')"
            />
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.description') }}</label>
            <textarea
              v-model="segmentForm.description"
              rows="2"
              class="input-field"
              :placeholder="$t('audience.segment.modal.descriptionPlaceholder')"
            />
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.conditions') }}</label>
            <input
              v-model="segmentForm.conditions"
              type="text"
              class="input-field"
              :placeholder="$t('audience.segment.modal.conditionsPlaceholder')"
            />
          </div>
          <div class="flex items-center gap-2">
            <input
              id="autoUpdate"
              v-model="segmentForm.autoUpdate"
              type="checkbox"
              class="rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
            />
            <label for="autoUpdate" class="text-body text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.autoUpdate') }}</label>
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-3">
          <button
            type="button"
            class="btn-secondary"
            @click="closeModal"
          >
            {{ $t('audience.segment.modal.cancel') }}
          </button>
          <button
            type="submit"
            :disabled="!segmentForm.name || creating"
            class="btn-primary"
          >
            {{ creating ? $t('audience.segment.modal.creating') : $t('audience.segment.modal.add') }}
          </button>
        </div>
      </form>
    </BaseModal>

    <!-- 세그먼트 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('audience.segment.deleteTitle')"
      :message="$t('audience.segment.confirmDelete')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDeleteSegment"
      @cancel="deleteTargetId = null"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('audience.segment.bulkDeleteTitle')"
      :message="$t('audience.segment.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  MagnifyingGlassIcon,
  PlusIcon,
  TrashIcon,
  UserGroupIcon,
  UsersIcon,
} from '@heroicons/vue/24/outline'
import { useAudienceStore } from '@/stores/audience'
import { useNotificationStore } from '@/stores/notification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import OTabs from '@/components/ui/OTabs.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import type { AudienceSegment } from '@/types/audience'

const { t } = useI18n({ useScope: 'global' })
const store = useAudienceStore()
const notificationStore = useNotificationStore()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const activeTab = ref('profiles')
const sortBy = ref('engagement_score')
const currentPage = ref(0)
const showSegmentModal = ref(false)
const creating = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

const segmentForm = reactive({
  name: '',
  description: '',
  conditions: '',
  autoUpdate: false,
})

const tabs = [
  { key: 'profiles', label: t('audience.tabs.profiles') },
  { key: 'segments', label: t('audience.tabs.segments') },
]

// --- 세그먼트 검색 · 정렬 · 선택 ---
const sortOptions = computed<ListSortOption<AudienceSegment>[]>(() => [
  {
    key: 'recent',
    label: t('audience.segment.sortRecent'),
    accessor: 'createdAt',
    kind: 'date',
    defaultDir: 'desc',
  },
  {
    key: 'members',
    label: t('audience.segment.sortMembers'),
    accessor: 'memberCount',
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'name', label: t('audience.segment.sortName'), accessor: 'name', kind: 'string', defaultDir: 'asc' },
])

const {
  query,
  sortKey,
  sortDir,
  filtered,
  visibleCount,
  isSourceEmpty,
  isResultEmpty,
  resetFilters,
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  toggleAll,
  clearSelection,
} = useListControls<AudienceSegment>(() => store.segments, {
  searchFields: ['name', 'description', 'conditions'],
  sortOptions,
  defaultSortKey: 'recent',
})

function scoreColor(score: number): string {
  if (score >= 80) return 'bg-success'
  if (score >= 50) return 'bg-warning'
  return 'bg-gray-400'
}

type AudiencePlatform = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH'

function platformCode(platform: string): AudiencePlatform | undefined {
  const normalized = platform.toUpperCase()
  if (normalized.includes('YOUTUBE') || normalized === 'YT') return 'YT'
  if (normalized.includes('INSTAGRAM') || normalized === 'IG') return 'IG'
  if (normalized.includes('TIKTOK') || normalized === 'TT') return 'TT'
  if (normalized.includes('FACEBOOK') || normalized === 'FB') return 'FB'
  if (normalized.includes('NAVER') || normalized === 'NV') return 'NV'
  if (normalized.includes('THREAD') || normalized === 'TH') return 'TH'
  return undefined
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

async function loadProfiles() {
  currentPage.value = 0
  await store.loadProfiles(sortBy.value, 0, 20)
}

async function loadSegments() {
  await store.loadSegments()
}

async function changePage(page: number) {
  currentPage.value = page
  await store.loadProfiles(sortBy.value, page, 20)
}

async function handleCreateSegment() {
  creating.value = true
  try {
    await store.createSegment({
      name: segmentForm.name,
      description: segmentForm.description || undefined,
      conditions: segmentForm.conditions || undefined,
      autoUpdate: segmentForm.autoUpdate,
    })
    closeModal()
  } catch (e) {
    console.error(t('audience.segment.createFailed'), e)
  } finally {
    creating.value = false
  }
}

function handleDeleteSegment(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

async function confirmDeleteSegment() {
  const id = deleteTargetId.value
  deleteTargetId.value = null
  if (id === null) return
  try {
    await store.deleteSegment(id)
  } catch (e) {
    console.error(t('audience.segment.deleteFailed'), e)
  }
}

async function handleBulkDelete() {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map(id => store.deleteSegment(id)))
    notificationStore.success(t('audience.segment.bulkDeleteDone', { count: ids.length }))
  } catch (e) {
    console.error(t('audience.segment.deleteFailed'), e)
    notificationStore.error(t('audience.segment.bulkDeleteFailed'))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

function closeModal() {
  showSegmentModal.value = false
  segmentForm.name = ''
  segmentForm.description = ''
  segmentForm.conditions = ''
  segmentForm.autoUpdate = false
}

onMounted(async () => {
  await Promise.all([store.loadProfiles(), store.loadSegments()])
})
</script>
