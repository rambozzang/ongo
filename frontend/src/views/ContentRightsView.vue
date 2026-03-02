<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ShieldCheckIcon,
  PlusIcon,
  BellAlertIcon,
} from '@heroicons/vue/24/outline'
import { useContentRightsStore } from '@/stores/contentRights'
import PageHeader from '@/components/common/PageHeader.vue'
import RightCard from '@/components/contentrights/RightCard.vue'
import AlertBanner from '@/components/contentrights/AlertBanner.vue'
import RightsSummaryCards from '@/components/contentrights/RightsSummaryCards.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OTabs from '@/components/ui/OTabs.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import type { ContentRight, AssetType, LicenseType } from '@/types/contentRights'

const store = useContentRightsStore()
const { rights, isLoading, activeRights, expiringRights, expiredRights, unreadAlerts } =
  storeToRefs(store)

// ─── UI State ──────────────────────────────────────
const activeTab = ref<'all' | 'active' | 'expiring' | 'expired'>('all')
const showCreateModal = ref(false)

// ─── Create Modal State ────────────────────────────
const newVideoId = ref('')
const newVideoTitle = ref('')
const newAssetName = ref('')
const newAssetType = ref<AssetType>('MUSIC')
const newLicenseType = ref<LicenseType>('ROYALTY_FREE')
const newSource = ref('')
const newLicenseUrl = ref('')
const newExpiresAt = ref('')
const newCost = ref(0)
const newCurrency = ref('KRW')
const newNotes = ref('')

const assetTypeOptions: { value: AssetType; label: string }[] = [
  { value: 'MUSIC', label: '음악' },
  { value: 'IMAGE', label: '이미지' },
  { value: 'FONT', label: '폰트' },
  { value: 'VIDEO_CLIP', label: '비디오 클립' },
  { value: 'SOUND_EFFECT', label: '효과음' },
  { value: 'OTHER', label: '기타' },
]

const licenseTypeOptions: { value: LicenseType; label: string }[] = [
  { value: 'FREE', label: '무료' },
  { value: 'ROYALTY_FREE', label: '로열티 프리' },
  { value: 'CREATIVE_COMMONS', label: 'Creative Commons' },
  { value: 'PAID', label: '유료' },
  { value: 'SUBSCRIPTION', label: '구독' },
  { value: 'CUSTOM', label: '기타' },
]

// ─── Computed ──────────────────────────────────────
const tabs = computed(() => [
  { key: 'all' as const, label: '전체', count: rights.value.length },
  { key: 'active' as const, label: '활성', count: activeRights.value.length },
  { key: 'expiring' as const, label: '만료임박', count: expiringRights.value.length },
  { key: 'expired' as const, label: '만료', count: expiredRights.value.length },
])

const filteredRights = computed(() => {
  switch (activeTab.value) {
    case 'active':
      return activeRights.value
    case 'expiring':
      return expiringRights.value
    case 'expired':
      return expiredRights.value
    default:
      return rights.value
  }
})

// ─── Handlers ──────────────────────────────────────
function openCreateModal() {
  newVideoId.value = ''
  newVideoTitle.value = ''
  newAssetName.value = ''
  newAssetType.value = 'MUSIC'
  newLicenseType.value = 'ROYALTY_FREE'
  newSource.value = ''
  newLicenseUrl.value = ''
  newExpiresAt.value = ''
  newCost.value = 0
  newCurrency.value = 'KRW'
  newNotes.value = ''
  showCreateModal.value = true
}

function closeCreateModal() {
  showCreateModal.value = false
}

async function handleCreateRight() {
  if (!newAssetName.value.trim() || !newVideoTitle.value.trim()) return

  await store.createRight({
    videoId: newVideoId.value.trim() || `v${Date.now()}`,
    videoTitle: newVideoTitle.value.trim(),
    assetName: newAssetName.value.trim(),
    assetType: newAssetType.value,
    licenseType: newLicenseType.value,
    source: newSource.value.trim(),
    licenseUrl: newLicenseUrl.value.trim() || undefined,
    expiresAt: newExpiresAt.value || undefined,
    cost: newCost.value,
    currency: newCurrency.value,
    notes: newNotes.value.trim() || undefined,
  })

  closeCreateModal()
}

function handleEditRight(right: ContentRight) {
  // For now, open create modal pre-filled (can be extended to full edit)
  newVideoId.value = right.videoId
  newVideoTitle.value = right.videoTitle
  newAssetName.value = right.assetName
  newAssetType.value = right.assetType
  newLicenseType.value = right.licenseType
  newSource.value = right.source
  newLicenseUrl.value = right.licenseUrl ?? ''
  newExpiresAt.value = right.expiresAt?.split('T')[0] ?? ''
  newCost.value = right.cost
  newCurrency.value = right.currency
  newNotes.value = right.notes ?? ''
  showCreateModal.value = true
}

function handleDeleteRight(id: number) {
  if (confirm('이 에셋 정보를 삭제하시겠습니까?')) {
    store.deleteRight(id)
  }
}

function handleFindAlternatives(id: number) {
  // Placeholder: could open a modal with alternatives
  alert(`에셋 ID ${id}의 대체 에셋 검색 기능은 곧 제공됩니다.`)
}

function handleDismissAlert(alertId: number) {
  store.markAlertRead(alertId)
}

onMounted(() => {
  store.fetchAll()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader title="콘텐츠 저작권 관리" description="영상에 사용된 에셋의 라이선스 상태를 관리하고 만료 알림을 받으세요">
      <template #title-suffix>
        <span
          v-if="unreadAlerts.length > 0"
          class="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-900/30 dark:text-red-400"
        >
          <BellAlertIcon class="h-3.5 w-3.5" />
          {{ unreadAlerts.length }}
        </span>
      </template>
      <template #actions>
        <button class="btn-primary inline-flex items-center gap-2" @click="openCreateModal">
          <PlusIcon class="h-5 w-5" />
          에셋 등록
        </button>
      </template>
    </PageHeader>

    <!-- Alert Banner -->
    <AlertBanner :alerts="unreadAlerts" @dismiss="handleDismissAlert" />

    <!-- Summary Cards -->
    <RightsSummaryCards :rights="rights" />

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Tabs -->
      <OTabs v-model="activeTab" :tabs="tabs" class="mb-6" />

      <!-- Rights Grid -->
      <div v-if="filteredRights.length > 0" class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <RightCard
          v-for="right in filteredRights"
          :key="right.id"
          :right="right"
          @edit="handleEditRight"
          @delete="handleDeleteRight"
          @find-alternatives="handleFindAlternatives"
        />
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
      >
        <ShieldCheckIcon class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ activeTab === 'all' ? '등록된 에셋이 없습니다' : '해당 상태의 에셋이 없습니다' }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          사용 중인 에셋을 등록하여 라이선스를 관리하세요
        </p>
        <button
          v-if="activeTab === 'all'"
          class="btn-primary mt-4 inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          첫 에셋 등록하기
        </button>
      </div>
    </template>

    <!-- Create Right Modal -->
    <BaseModal v-model="showCreateModal" title="에셋 등록" max-width="lg">
      <div class="max-h-[60vh] overflow-y-auto space-y-4">
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">에셋 이름 *</label>
          <input v-model="newAssetName" type="text" placeholder="예: Spring Vibes - BGM" class="input-field" />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">에셋 유형</label>
          <select v-model="newAssetType" class="input-field">
            <option v-for="opt in assetTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">사용 영상 제목 *</label>
          <input v-model="newVideoTitle" type="text" placeholder="예: 봄 메이크업 튜토리얼" class="input-field" />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">라이선스 유형</label>
          <select v-model="newLicenseType" class="input-field">
            <option v-for="opt in licenseTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">출처</label>
          <input v-model="newSource" type="text" placeholder="예: Artlist, Unsplash, Google Fonts" class="input-field" />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">라이선스 URL</label>
          <input v-model="newLicenseUrl" type="url" placeholder="https://..." class="input-field" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">만료일</label>
            <input v-model="newExpiresAt" type="date" class="input-field" />
          </div>
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">비용 (원)</label>
            <input v-model.number="newCost" type="number" min="0" class="input-field" />
          </div>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">메모</label>
          <textarea v-model="newNotes" rows="2" placeholder="라이선스 관련 추가 메모" class="input-field" />
        </div>
      </div>
      <template #footer>
        <button class="btn-secondary" @click="closeCreateModal">취소</button>
        <button class="btn-primary" :disabled="!newAssetName.trim() || !newVideoTitle.trim()" @click="handleCreateRight">등록</button>
      </template>
    </BaseModal>
  </div>
</template>
