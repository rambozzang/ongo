<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="t('brandDeal.title')" :description="t('brandDeal.description')">
      <template #actions>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showCreateModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          {{ t('brandDeal.addDeal') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="t('brandDeal.pageGuideTitle')" :items="(tm('brandDeal.pageGuide') as string[])" />

    <!-- 탭 -->
    <OTabs v-model="activeTab" :tabs="tabs" class="mb-6" />

    <!-- 딜 트래커 탭 -->
    <div v-if="activeTab === 'tracker'">
      <!-- 검색 · 상태 필터 · 정렬 · 일괄 작업 -->
      <ListToolbar
        v-if="!isSourceEmpty"
        v-model="query"
        v-model:sort-key="sortKey"
        v-model:sort-dir="sortDir"
        :sort-options="sortOptions"
        :selected-count="selectedCount"
        :total-count="visibleCount"
        :search-placeholder="t('brandDeal.searchPlaceholder')"
        :search-label="t('brandDeal.searchLabel')"
        @clear-selection="clearSelection"
      >
        <template #filters>
          <select v-model="statusFilter" class="input-field w-auto min-w-[8.5rem]" :aria-label="t('action.filter')">
            <option value="">{{ t('brandDeal.status.all') }}</option>
            <option value="INQUIRY">{{ t('brandDeal.status.inquiry') }}</option>
            <option value="NEGOTIATION">{{ t('brandDeal.status.negotiation') }}</option>
            <option value="CONTRACTED">{{ t('brandDeal.status.contracted') }}</option>
            <option value="IN_PROGRESS">{{ t('brandDeal.status.inProgress') }}</option>
            <option value="COMPLETED">{{ t('brandDeal.status.completed') }}</option>
            <option value="CANCELLED">{{ t('brandDeal.status.cancelled') }}</option>
          </select>
        </template>

        <template #bulk-actions>
          <button
            type="button"
            class="btn-danger inline-flex items-center gap-1.5"
            :disabled="bulkDeleting"
            @click="showBulkDeleteModal = true"
          >
            <TrashIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('list.bulkDelete') }}
          </button>
        </template>
      </ListToolbar>

      <!-- 전체 선택 -->
      <div v-if="!isSourceEmpty && visibleCount > 0" class="mb-3 flex items-center gap-2">
        <input
          id="deals-select-all"
          type="checkbox"
          :class="CHECKBOX_CLASS"
          :checked="allSelected"
          :indeterminate="someSelected"
          @change="toggleAll"
        />
        <label for="deals-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
          {{ t('list.selectAll', { count: visibleCount }) }}
        </label>
      </div>

      <AsyncState
        :loading="store.loading && isSourceEmpty"
        :empty="isSourceEmpty"
        skeleton="card"
        :skeleton-count="3"
        :empty-icon="BriefcaseIcon"
        :empty-title="t('brandDeal.emptyDeals')"
        :empty-description="t('brandDeal.emptyDealsDescription')"
        :empty-action-label="t('brandDeal.addDeal')"
        :retryable="false"
        @empty-action="showCreateModal = true"
      >
        <!-- 검색·필터 결과만 비었을 때 -->
        <EmptyState
          v-if="isResultEmpty"
          :icon="MagnifyingGlassIcon"
          :title="t('list.noResultsTitle')"
          :description="t('list.noResultsDescription')"
          :action-label="t('list.resetFilters')"
          @action="resetSearchAndFilters"
        />

        <!-- 딜 카드 목록 -->
        <div v-else class="page-grid page-grid--cards">
          <div
            v-for="deal in filtered"
            :key="deal.id"
            class="flex items-start gap-2"
          >
            <input
              type="checkbox"
              :class="[CHECKBOX_CLASS, 'mt-5']"
              :checked="isSelected(deal.id)"
              :aria-label="t('list.selectItem', { name: deal.brandName })"
              @change="toggle(deal.id)"
            />
            <div class="min-w-0 flex-1 space-y-3 rounded-[11px] border border-line bg-surface-card p-4 transition-colors hover:bg-surface-raised">
              <div class="flex items-start justify-between">
                <h3 class="truncate text-h3 text-gray-900 dark:text-gray-100">{{ deal.brandName }}</h3>
                <span
                  :class="[
                    'inline-flex items-center rounded-full px-2.5 py-0.5 text-caption',
                    statusBadgeClass(deal.status),
                  ]"
                >
                  {{ statusLabel(deal.status) }}
                </span>
              </div>

              <div v-if="deal.dealValue != null" class="text-title font-bold text-primary-600 dark:text-primary-400">
                {{ formatKRW(deal.dealValue) }}
              </div>

              <div class="space-y-1 text-body text-gray-500 dark:text-gray-400">
                <div v-if="deal.contactName" class="flex items-center gap-1">
                  <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('brandDeal.contactPerson') }}</span> {{ deal.contactName }}
                </div>
                <div v-if="deal.deadline" class="flex items-center gap-1">
                  <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('brandDeal.deadline') }}</span> {{ deal.deadline }}
                </div>
              </div>

              <div class="flex justify-end pt-2 border-t border-gray-100 dark:border-gray-700">
                <button
                  class="text-body-xs text-error-strong transition hover:opacity-80"
                  @click="handleDeleteDeal(deal.id)"
                >
                  {{ t('brandDeal.delete') }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </AsyncState>
    </div>

    <!-- 미디어키트 탭 -->
    <div v-if="activeTab === 'mediakit'">
      <div class="rounded-[11px] border border-line bg-surface-card p-4 space-y-5">
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.displayName') }}</label>
          <input
            v-model="mkForm.displayName"
            type="text"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.displayNamePlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.bio') }}</label>
          <textarea
            v-model="mkForm.bio"
            rows="3"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.bioPlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.categories') }}</label>
          <input
            v-model="mkCategoriesInput"
            type="text"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.categoriesPlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.socialLinks') }}</label>
          <div class="space-y-2">
            <div v-for="platform in socialPlatforms" :key="platform" class="flex items-center gap-2">
              <span class="w-24 text-body font-medium text-gray-600 dark:text-gray-300">{{ platform }}</span>
              <input
                v-model="mkSocialLinks[platform]"
                type="url"
                class="input-field flex-1"
                :placeholder="`https://${platform.toLowerCase()}.com/...`"
              />
            </div>
          </div>
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.slug') }}</label>
          <input
            v-model="mkForm.slug"
            type="text"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.slugPlaceholder')"
          />
        </div>
        <div class="flex items-center gap-2">
          <input
            id="isPublic"
            v-model="mkForm.isPublic"
            type="checkbox"
            class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
          />
          <label for="isPublic" class="text-body text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.isPublic') }}</label>
        </div>
        <div class="flex justify-end">
          <button
            class="btn-primary"
            :disabled="store.loading"
            @click="handleSaveMediaKit"
          >
            {{ t('brandDeal.mediaKit.save') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 새 딜 추가 모달 -->
    <BaseModal v-model="showCreateModal" :title="t('brandDeal.modal.title')" max-width="lg">
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.brandName') }}</label>
            <input
              v-model="newDeal.brandName"
              type="text"
              class="input-field"
              :placeholder="t('brandDeal.modal.brandNamePlaceholder')"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.contactPerson') }}</label>
              <input
                v-model="newDeal.contactName"
                type="text"
                class="input-field"
                :placeholder="t('brandDeal.modal.contactNamePlaceholder')"
              />
            </div>
            <div>
              <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.email') }}</label>
              <input
                v-model="newDeal.contactEmail"
                type="email"
                class="input-field"
                placeholder="email@brand.com"
              />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.dealValue') }}</label>
              <input
                v-model.number="newDeal.dealValue"
                type="number"
                class="input-field"
                placeholder="0"
              />
            </div>
            <div>
              <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.deadline') }}</label>
              <input
                v-model="newDeal.deadline"
                type="date"
                class="input-field"
              />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.notes') }}</label>
            <textarea
              v-model="newDeal.notes"
              rows="3"
              class="input-field"
              :placeholder="t('brandDeal.modal.notesPlaceholder')"
            />
          </div>
          <template #footer>
            <button
              class="btn-secondary"
              @click="showCreateModal = false"
            >
              {{ t('brandDeal.modal.cancel') }}
            </button>
            <button
              class="btn-primary"
              :disabled="!newDeal.brandName.trim()"
              @click="handleCreateDeal"
            >
              {{ t('brandDeal.modal.add') }}
            </button>
          </template>
    </BaseModal>

    <!-- 딜 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('brandDeal.deleteTitle')"
      :message="$t('brandDeal.confirmDelete')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDeleteDeal"
      @cancel="deleteTargetId = null"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('brandDeal.bulkDeleteTitle')"
      :message="$t('brandDeal.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon, TrashIcon, BriefcaseIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'
import { useBrandDealStore } from '@/stores/branddeal'
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
import type { BrandDeal } from '@/types/branddeal'

const { t, tm } = useI18n({ useScope: 'global' })
const store = useBrandDealStore()
const notificationStore = useNotificationStore()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const activeTab = ref('tracker')
/** 상태 필터 — 예전에는 서버 재조회(`loadDeals(status)`)였지만, "원본이 비었는지"와
 *  "필터 결과만 비었는지"를 구분하려면 원본 배열이 유지되어야 하므로 클라이언트 필터로 옮겼다. */
const statusFilter = ref('')
const showCreateModal = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)

const newDeal = reactive({
  brandName: '',
  contactName: '',
  contactEmail: '',
  dealValue: undefined as number | undefined,
  deadline: '',
  notes: '',
})

// 미디어키트 폼
const mkForm = reactive({
  displayName: '',
  bio: '',
  slug: '',
  isPublic: false,
})
const mkCategoriesInput = ref('')
const mkSocialLinks = reactive<Record<string, string>>({})
const socialPlatforms = ['YouTube', 'Instagram', 'TikTok', 'Twitter']

const tabs = [
  { key: 'tracker', label: t('brandDeal.tabs.tracker') },
  { key: 'mediakit', label: t('brandDeal.tabs.mediakit') },
]

const statusLabelMap: Record<string, string> = {
  INQUIRY: 'brandDeal.status.inquiry',
  NEGOTIATION: 'brandDeal.status.negotiation',
  CONTRACTED: 'brandDeal.status.contracted',
  IN_PROGRESS: 'brandDeal.status.inProgress',
  COMPLETED: 'brandDeal.status.completed',
  CANCELLED: 'brandDeal.status.cancelled',
}

function statusLabel(status: string): string {
  const key = statusLabelMap[status]
  return key ? t(key) : status
}

function statusBadgeClass(status: string): string {
  const map: Record<string, string> = {
    INQUIRY: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    NEGOTIATION: 'bg-warning-subtle text-warning-strong',
    CONTRACTED: 'bg-info-subtle text-info-strong',
    IN_PROGRESS: 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400',
    COMPLETED: 'bg-success-subtle text-success-strong',
    CANCELLED: 'bg-error-subtle text-error-strong',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

// --- 검색 · 정렬 · 선택 ---
const sortOptions = computed<ListSortOption<BrandDeal>[]>(() => [
  { key: 'recent', label: t('brandDeal.sortRecent'), accessor: 'createdAt', kind: 'date', defaultDir: 'desc' },
  { key: 'deadline', label: t('brandDeal.sortDeadline'), accessor: 'deadline', kind: 'date', defaultDir: 'asc' },
  { key: 'value', label: t('brandDeal.sortValue'), accessor: 'dealValue', kind: 'number', defaultDir: 'desc' },
  { key: 'brand', label: t('brandDeal.sortBrand'), accessor: 'brandName', kind: 'string', defaultDir: 'asc' },
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
} = useListControls<BrandDeal>(() => store.deals, {
  searchFields: ['brandName', 'contactName', 'contactEmail', 'notes', 'deliverables'],
  sortOptions,
  defaultSortKey: 'recent',
  filters: computed(() =>
    statusFilter.value === '' ? [] : [(deal: BrandDeal) => deal.status === statusFilter.value],
  ),
})

const resetSearchAndFilters = () => {
  resetFilters()
  statusFilter.value = ''
}

function resetNewDeal() {
  newDeal.brandName = ''
  newDeal.contactName = ''
  newDeal.contactEmail = ''
  newDeal.dealValue = undefined
  newDeal.deadline = ''
  newDeal.notes = ''
}

async function handleCreateDeal() {
  if (!newDeal.brandName.trim()) return
  try {
    await store.createDeal({
      brandName: newDeal.brandName,
      contactName: newDeal.contactName || undefined,
      contactEmail: newDeal.contactEmail || undefined,
      dealValue: newDeal.dealValue,
      deadline: newDeal.deadline || undefined,
      notes: newDeal.notes || undefined,
    })
    showCreateModal.value = false
    resetNewDeal()
  } catch (e) {
    console.error(t('brandDeal.createFailed'), e)
  }
}

function handleDeleteDeal(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

async function confirmDeleteDeal() {
  const id = deleteTargetId.value
  deleteTargetId.value = null
  if (id === null) return
  try {
    await store.deleteDeal(id)
  } catch (e) {
    console.error(t('brandDeal.deleteFailed'), e)
  }
}

async function handleBulkDelete() {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map(id => store.deleteDeal(id)))
    notificationStore.success(t('brandDeal.bulkDeleteDone', { count: ids.length }))
  } catch (e) {
    console.error(t('brandDeal.deleteFailed'), e)
    notificationStore.error(t('brandDeal.bulkDeleteFailed'))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

async function handleSaveMediaKit() {
  const categories = mkCategoriesInput.value
    .split(',')
    .map(c => c.trim())
    .filter(Boolean)
  const socialLinks: Record<string, string> = {}
  for (const p of socialPlatforms) {
    if (mkSocialLinks[p]) {
      socialLinks[p] = mkSocialLinks[p]
    }
  }
  try {
    await store.saveMediaKit({
      displayName: mkForm.displayName || undefined,
      bio: mkForm.bio || undefined,
      categories: categories.length > 0 ? categories : undefined,
      socialLinks: Object.keys(socialLinks).length > 0 ? socialLinks : undefined,
      isPublic: mkForm.isPublic,
      slug: mkForm.slug || undefined,
    })
  } catch (e) {
    console.error(t('brandDeal.mediaKit.saveFailed'), e)
  }
}

function populateMediaKitForm() {
  if (store.mediaKit) {
    mkForm.displayName = store.mediaKit.displayName ?? ''
    mkForm.bio = store.mediaKit.bio ?? ''
    mkForm.slug = store.mediaKit.slug ?? ''
    mkForm.isPublic = store.mediaKit.isPublic ?? false
    mkCategoriesInput.value = (store.mediaKit.categories ?? []).join(', ')
    for (const p of socialPlatforms) {
      mkSocialLinks[p] = store.mediaKit.socialLinks?.[p] ?? ''
    }
  }
}

watch(() => activeTab.value, async (tab) => {
  if (tab === 'mediakit' && !store.mediaKit) {
    await store.loadMediaKit()
    populateMediaKitForm()
  }
})

onMounted(async () => {
  await store.loadDeals()
})
</script>
