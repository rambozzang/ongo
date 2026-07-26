<template>
  <div class="relative">
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
      <div class="mb-4">
        <select v-model="statusFilter" class="input-field" @change="filterDeals">
          <option value="">{{ t('brandDeal.status.all') }}</option>
          <option value="INQUIRY">{{ t('brandDeal.status.inquiry') }}</option>
          <option value="NEGOTIATION">{{ t('brandDeal.status.negotiation') }}</option>
          <option value="CONTRACTED">{{ t('brandDeal.status.contracted') }}</option>
          <option value="IN_PROGRESS">{{ t('brandDeal.status.inProgress') }}</option>
          <option value="COMPLETED">{{ t('brandDeal.status.completed') }}</option>
          <option value="CANCELLED">{{ t('brandDeal.status.cancelled') }}</option>
        </select>
      </div>

      <!-- 로딩 -->
      <div v-if="store.loading" class="py-12 text-center text-gray-400 dark:text-gray-500">
        {{ t('brandDeal.loading') }}
      </div>

      <!-- 빈 상태 -->
      <div v-else-if="store.deals.length === 0" class="py-12 text-center text-gray-400 dark:text-gray-500">
        {{ t('brandDeal.emptyDeals') }}
      </div>

      <!-- 딜 카드 목록 -->
      <div v-else class="page-grid page-grid--cards">
        <div
          v-for="deal in store.deals"
          :key="deal.id"
          class="card space-y-3 transition-shadow hover:shadow-md"
        >
          <div class="flex items-start justify-between">
            <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">{{ deal.brandName }}</h3>
            <span
              :class="[
                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                statusBadgeClass(deal.status),
              ]"
            >
              {{ statusLabel(deal.status) }}
            </span>
          </div>

          <div v-if="deal.dealValue != null" class="text-lg font-bold text-primary-600 dark:text-primary-400">
            {{ formatKRW(deal.dealValue) }}
          </div>

          <div class="space-y-1 text-sm text-gray-500 dark:text-gray-400">
            <div v-if="deal.contactName" class="flex items-center gap-1">
              <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('brandDeal.contactPerson') }}</span> {{ deal.contactName }}
            </div>
            <div v-if="deal.deadline" class="flex items-center gap-1">
              <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('brandDeal.deadline') }}</span> {{ deal.deadline }}
            </div>
          </div>

          <div class="flex justify-end pt-2 border-t border-gray-100 dark:border-gray-700">
            <button
              class="text-xs text-red-500 hover:text-red-700"
              @click="handleDeleteDeal(deal.id)"
            >
              {{ t('brandDeal.delete') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 미디어키트 탭 -->
    <div v-if="activeTab === 'mediakit'">
      <div class="card space-y-5">
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.displayName') }}</label>
          <input
            v-model="mkForm.displayName"
            type="text"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.displayNamePlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.bio') }}</label>
          <textarea
            v-model="mkForm.bio"
            rows="3"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.bioPlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.categories') }}</label>
          <input
            v-model="mkCategoriesInput"
            type="text"
            class="input-field"
            :placeholder="t('brandDeal.mediaKit.categoriesPlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.socialLinks') }}</label>
          <div class="space-y-2">
            <div v-for="platform in socialPlatforms" :key="platform" class="flex items-center gap-2">
              <span class="w-24 text-sm font-medium text-gray-600 dark:text-gray-300">{{ platform }}</span>
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
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.slug') }}</label>
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
          <label for="isPublic" class="text-sm text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.isPublic') }}</label>
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
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.brandName') }}</label>
            <input
              v-model="newDeal.brandName"
              type="text"
              class="input-field"
              :placeholder="t('brandDeal.modal.brandNamePlaceholder')"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.contactPerson') }}</label>
              <input
                v-model="newDeal.contactName"
                type="text"
                class="input-field"
                :placeholder="t('brandDeal.modal.contactNamePlaceholder')"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.email') }}</label>
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
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.dealValue') }}</label>
              <input
                v-model.number="newDeal.dealValue"
                type="number"
                class="input-field"
                placeholder="0"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.deadline') }}</label>
              <input
                v-model="newDeal.deadline"
                type="date"
                class="input-field"
              />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.notes') }}</label>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon } from '@heroicons/vue/24/outline'
import { useBrandDealStore } from '@/stores/branddeal'
import OTabs from '@/components/ui/OTabs.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

const { t, tm } = useI18n({ useScope: 'global' })
const store = useBrandDealStore()

const activeTab = ref('tracker')
const statusFilter = ref('')
const showCreateModal = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)

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
    NEGOTIATION: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    CONTRACTED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    IN_PROGRESS: 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400',
    COMPLETED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    CANCELLED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

async function filterDeals() {
  await store.loadDeals(statusFilter.value || undefined)
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
