<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">{{ t('brandDeal.title') }}</h1>
    </div>

    <!-- 탭 -->
    <div class="border-b border-gray-200">
      <nav class="flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'pb-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.key
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-gray-500 hover:text-gray-700',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 딜 트래커 탭 -->
    <div v-if="activeTab === 'tracker'">
      <div class="mb-4 flex items-center justify-between">
        <select v-model="statusFilter" class="px-3 py-2 border rounded-lg text-sm" @change="filterDeals">
          <option value="">{{ t('brandDeal.status.all') }}</option>
          <option value="INQUIRY">{{ t('brandDeal.status.inquiry') }}</option>
          <option value="NEGOTIATION">{{ t('brandDeal.status.negotiation') }}</option>
          <option value="CONTRACTED">{{ t('brandDeal.status.contracted') }}</option>
          <option value="IN_PROGRESS">{{ t('brandDeal.status.inProgress') }}</option>
          <option value="COMPLETED">{{ t('brandDeal.status.completed') }}</option>
          <option value="CANCELLED">{{ t('brandDeal.status.cancelled') }}</option>
        </select>
        <button
          class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700"
          @click="showCreateModal = true"
        >
          {{ t('brandDeal.addDeal') }}
        </button>
      </div>

      <!-- 로딩 -->
      <div v-if="store.loading" class="text-center py-12 text-gray-400">
        {{ t('brandDeal.loading') }}
      </div>

      <!-- 빈 상태 -->
      <div v-else-if="store.deals.length === 0" class="text-center py-12 text-gray-400">
        {{ t('brandDeal.emptyDeals') }}
      </div>

      <!-- 딜 카드 목록 -->
      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="deal in store.deals"
          :key="deal.id"
          class="bg-white rounded-lg border p-5 space-y-3 hover:shadow-md transition-shadow"
        >
          <div class="flex items-start justify-between">
            <h3 class="text-base font-semibold text-gray-900 truncate">{{ deal.brandName }}</h3>
            <span
              :class="[
                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                statusBadgeClass(deal.status),
              ]"
            >
              {{ statusLabel(deal.status) }}
            </span>
          </div>

          <div v-if="deal.dealValue != null" class="text-lg font-bold text-indigo-600">
            {{ formatKRW(deal.dealValue) }}
          </div>

          <div class="space-y-1 text-sm text-gray-500">
            <div v-if="deal.contactName" class="flex items-center gap-1">
              <span class="font-medium text-gray-600">{{ t('brandDeal.contactPerson') }}</span> {{ deal.contactName }}
            </div>
            <div v-if="deal.deadline" class="flex items-center gap-1">
              <span class="font-medium text-gray-600">{{ t('brandDeal.deadline') }}</span> {{ deal.deadline }}
            </div>
          </div>

          <div class="flex justify-end pt-2 border-t border-gray-100">
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
      <div class="bg-white rounded-lg border p-6 space-y-5">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.mediaKit.displayName') }}</label>
          <input
            v-model="mkForm.displayName"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            :placeholder="t('brandDeal.mediaKit.displayNamePlaceholder')"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.mediaKit.bio') }}</label>
          <textarea
            v-model="mkForm.bio"
            rows="3"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            :placeholder="t('brandDeal.mediaKit.bioPlaceholder')"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.mediaKit.categories') }}</label>
          <input
            v-model="mkCategoriesInput"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            :placeholder="t('brandDeal.mediaKit.categoriesPlaceholder')"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.mediaKit.socialLinks') }}</label>
          <div class="space-y-2">
            <div v-for="platform in socialPlatforms" :key="platform" class="flex items-center gap-2">
              <span class="text-sm font-medium text-gray-600 w-24">{{ platform }}</span>
              <input
                v-model="mkSocialLinks[platform]"
                type="url"
                class="flex-1 px-3 py-2 border rounded-lg text-sm"
                :placeholder="`https://${platform.toLowerCase()}.com/...`"
              />
            </div>
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.mediaKit.slug') }}</label>
          <input
            v-model="mkForm.slug"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            :placeholder="t('brandDeal.mediaKit.slugPlaceholder')"
          />
        </div>
        <div class="flex items-center gap-2">
          <input
            id="isPublic"
            v-model="mkForm.isPublic"
            type="checkbox"
            class="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
          />
          <label for="isPublic" class="text-sm text-gray-700">{{ t('brandDeal.mediaKit.isPublic') }}</label>
        </div>
        <div class="flex justify-end">
          <button
            class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
            :disabled="store.loading"
            @click="handleSaveMediaKit"
          >
            {{ t('brandDeal.mediaKit.save') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 새 딜 추가 모달 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true" :aria-label="t('brandDeal.modal.title')" @click.self="showCreateModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-lg p-6 space-y-4">
        <h2 class="text-lg font-bold text-gray-900">{{ t('brandDeal.modal.title') }}</h2>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.modal.brandName') }}</label>
          <input
            v-model="newDeal.brandName"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            :placeholder="t('brandDeal.modal.brandNamePlaceholder')"
          />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.modal.contactPerson') }}</label>
            <input
              v-model="newDeal.contactName"
              type="text"
              class="w-full px-3 py-2 border rounded-lg text-sm"
              :placeholder="t('brandDeal.modal.contactNamePlaceholder')"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.modal.email') }}</label>
            <input
              v-model="newDeal.contactEmail"
              type="email"
              class="w-full px-3 py-2 border rounded-lg text-sm"
              placeholder="email@brand.com"
            />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.modal.dealValue') }}</label>
            <input
              v-model.number="newDeal.dealValue"
              type="number"
              class="w-full px-3 py-2 border rounded-lg text-sm"
              placeholder="0"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.modal.deadline') }}</label>
            <input
              v-model="newDeal.deadline"
              type="date"
              class="w-full px-3 py-2 border rounded-lg text-sm"
            />
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('brandDeal.modal.notes') }}</label>
          <textarea
            v-model="newDeal.notes"
            rows="3"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            :placeholder="t('brandDeal.modal.notesPlaceholder')"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button
            class="px-4 py-2 border rounded-lg text-sm text-gray-700 hover:bg-gray-50"
            @click="showCreateModal = false"
          >
            {{ t('brandDeal.modal.cancel') }}
          </button>
          <button
            class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
            :disabled="!newDeal.brandName.trim()"
            @click="handleCreateDeal"
          >
            {{ t('brandDeal.modal.add') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBrandDealStore } from '@/stores/branddeal'

const { t } = useI18n()
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
    INQUIRY: 'bg-gray-100 text-gray-700',
    NEGOTIATION: 'bg-yellow-100 text-yellow-700',
    CONTRACTED: 'bg-blue-100 text-blue-700',
    IN_PROGRESS: 'bg-indigo-100 text-indigo-700',
    COMPLETED: 'bg-green-100 text-green-700',
    CANCELLED: 'bg-red-100 text-red-700',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700'
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
