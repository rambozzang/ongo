<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ t('brandDeal.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ t('brandDeal.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showCreateModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          {{ t('brandDeal.addDeal') }}
        </button>
      </div>
    </div>

    <PageGuide :title="t('brandDeal.pageGuideTitle')" :items="(tm('brandDeal.pageGuide') as string[])" />

    <!-- 탭 -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'pb-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.key
              ? 'border-primary-600 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 딜 트래커 탭 -->
    <div v-if="activeTab === 'tracker'">
      <div class="mb-4">
        <select v-model="statusFilter" class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300" @change="filterDeals">
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
      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="deal in store.deals"
          :key="deal.id"
          class="rounded-lg border border-gray-200 bg-white p-5 space-y-3 transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
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
      <div class="rounded-lg border border-gray-200 bg-white p-6 space-y-5 dark:border-gray-700 dark:bg-gray-800">
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.displayName') }}</label>
          <input
            v-model="mkForm.displayName"
            type="text"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            :placeholder="t('brandDeal.mediaKit.displayNamePlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.bio') }}</label>
          <textarea
            v-model="mkForm.bio"
            rows="3"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            :placeholder="t('brandDeal.mediaKit.bioPlaceholder')"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.mediaKit.categories') }}</label>
          <input
            v-model="mkCategoriesInput"
            type="text"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
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
                class="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
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
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            :placeholder="t('brandDeal.mediaKit.slugPlaceholder')"
          />
        </div>
        <div class="flex items-center gap-2">
          <input
            id="isPublic"
            v-model="mkForm.isPublic"
            type="checkbox"
            class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
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
    <Teleport to="body">
      <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true" :aria-label="t('brandDeal.modal.title')" @click.self="showCreateModal = false">
        <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl space-y-4 dark:bg-gray-800">
          <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('brandDeal.modal.title') }}</h2>
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.brandName') }}</label>
            <input
              v-model="newDeal.brandName"
              type="text"
              class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              :placeholder="t('brandDeal.modal.brandNamePlaceholder')"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.contactPerson') }}</label>
              <input
                v-model="newDeal.contactName"
                type="text"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                :placeholder="t('brandDeal.modal.contactNamePlaceholder')"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.email') }}</label>
              <input
                v-model="newDeal.contactEmail"
                type="email"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
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
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                placeholder="0"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.deadline') }}</label>
              <input
                v-model="newDeal.deadline"
                type="date"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('brandDeal.modal.notes') }}</label>
            <textarea
              v-model="newDeal.notes"
              rows="3"
              class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              :placeholder="t('brandDeal.modal.notesPlaceholder')"
            />
          </div>
          <div class="flex justify-end gap-3 pt-2">
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
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon } from '@heroicons/vue/24/outline'
import { useBrandDealStore } from '@/stores/branddeal'
import PageGuide from '@/components/common/PageGuide.vue'

const { t, tm } = useI18n({ useScope: 'global' })
const store = useBrandDealStore()

const activeTab = ref('tracker')
const statusFilter = ref('')
const showCreateModal = ref(false)

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

async function handleDeleteDeal(id: number) {
  if (!confirm(t('brandDeal.confirmDelete'))) return
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
