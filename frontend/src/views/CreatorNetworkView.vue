<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('creatorNetwork.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('creatorNetwork.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <select
          v-model="platformFilter"
          class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
        >
          <option value="">{{ $t('creatorNetwork.allPlatforms') }}</option>
          <option value="youtube">YouTube</option>
          <option value="tiktok">TikTok</option>
          <option value="instagram">Instagram</option>
          <option value="naverclip">Naver Clip</option>
        </select>
      </div>
    </div>

    <PageGuide
      :title="$t('creatorNetwork.pageGuideTitle')"
      :items="($tm('creatorNetwork.pageGuide') as string[])"
    />

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Stats -->
      <div v-if="store.summary" class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3 desktop:grid-cols-5">
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('creatorNetwork.totalConnections') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.totalConnections }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('creatorNetwork.pendingRequests') }}
          </p>
          <p class="mt-1 text-xl font-bold text-yellow-600 dark:text-yellow-400">
            {{ store.summary.pendingRequests }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('creatorNetwork.sentRequests') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.sentRequests }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('creatorNetwork.avgMatchScore') }}
          </p>
          <p class="mt-1 text-xl font-bold text-primary-600 dark:text-primary-400">
            {{ store.summary.avgMatchScore }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('creatorNetwork.collabCompleted') }}
          </p>
          <p class="mt-1 text-xl font-bold text-green-600 dark:text-green-400">
            {{ store.summary.collabCompleted }}
          </p>
        </div>
      </div>

      <!-- Category Filter -->
      <div class="mb-6 flex flex-wrap gap-2">
        <button
          v-for="cat in categoryOptions"
          :key="cat.value"
          class="rounded-full px-3 py-1.5 text-xs font-medium transition-colors"
          :class="
            categoryFilter === cat.value
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'
          "
          @click="categoryFilter = cat.value"
        >
          {{ cat.label }}
        </button>
      </div>

      <!-- Creator Cards Grid -->
      <div v-if="filteredCreators.length > 0" class="mb-8 grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3 wide:grid-cols-4">
        <CreatorCard
          v-for="creator in filteredCreators"
          :key="creator.id"
          :creator="creator"
          @connect="handleConnect"
          @disconnect="handleDisconnect"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="mb-8 flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
      >
        <svg class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
        </svg>
        <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('creatorNetwork.noCreators') }}</p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('creatorNetwork.noCreatorsDesc') }}</p>
      </div>

      <!-- Collaboration Requests Section -->
      <div class="mt-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('creatorNetwork.collabRequests') }}
        </h2>

        <div v-if="store.collabRequests.length === 0" class="flex items-center justify-center rounded-xl border border-dashed border-gray-300 py-12 dark:border-gray-600">
          <p class="text-sm text-gray-400 dark:text-gray-500">{{ $t('creatorNetwork.noCollabRequests') }}</p>
        </div>

        <div v-else class="space-y-3">
          <CollabRequestRow
            v-for="req in store.collabRequests"
            :key="req.id"
            :request="req"
            :is-received="req.receiverId === 0"
            @respond="handleRespondCollab"
          />
        </div>
      </div>
    </template>

    <!-- Toast -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="translate-y-2 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-2 opacity-0"
    >
      <div
        v-if="showToast"
        class="fixed bottom-8 right-8 z-50 rounded-lg bg-gray-900 px-4 py-3 text-white shadow-lg dark:bg-gray-100 dark:text-gray-900"
      >
        {{ toastMessage }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCreatorNetworkStore } from '@/stores/creatorNetwork'
import type { CreatorCategory } from '@/types/creatorNetwork'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import CreatorCard from '@/components/creatornetwork/CreatorCard.vue'
import CollabRequestRow from '@/components/creatornetwork/CollabRequestRow.vue'

const { t } = useI18n({ useScope: 'global' })
const store = useCreatorNetworkStore()

const categoryFilter = ref<CreatorCategory | ''>('')
const platformFilter = ref('')
const showToast = ref(false)
const toastMessage = ref('')

const categoryOptions = computed(() => [
  { value: '' as const, label: t('creatorNetwork.allCategories') },
  { value: 'BEAUTY' as CreatorCategory, label: t('creatorNetwork.catBeauty') },
  { value: 'GAMING' as CreatorCategory, label: t('creatorNetwork.catGaming') },
  { value: 'FOOD' as CreatorCategory, label: t('creatorNetwork.catFood') },
  { value: 'TECH' as CreatorCategory, label: t('creatorNetwork.catTech') },
  { value: 'TRAVEL' as CreatorCategory, label: t('creatorNetwork.catTravel') },
  { value: 'MUSIC' as CreatorCategory, label: t('creatorNetwork.catMusic') },
  { value: 'EDUCATION' as CreatorCategory, label: t('creatorNetwork.catEducation') },
  { value: 'LIFESTYLE' as CreatorCategory, label: t('creatorNetwork.catLifestyle') },
  { value: 'FITNESS' as CreatorCategory, label: t('creatorNetwork.catFitness') },
  { value: 'OTHER' as CreatorCategory, label: t('creatorNetwork.catOther') },
])

const filteredCreators = computed(() => {
  return store.creators.filter((c) => {
    if (categoryFilter.value && c.category !== categoryFilter.value) return false
    if (platformFilter.value && c.platform !== platformFilter.value) return false
    return true
  })
})

let toastTimeout: ReturnType<typeof setTimeout> | null = null

function showToastMessage(message: string) {
  toastMessage.value = message
  showToast.value = true
  if (toastTimeout) clearTimeout(toastTimeout)
  toastTimeout = setTimeout(() => {
    showToast.value = false
  }, 3000)
}

async function handleConnect(creatorId: number) {
  await store.connectCreator(creatorId)
  showToastMessage(t('creatorNetwork.connectSuccess'))
}

async function handleDisconnect(creatorId: number) {
  if (!confirm(t('creatorNetwork.confirmDisconnect'))) return
  await store.disconnectCreator(creatorId)
  showToastMessage(t('creatorNetwork.disconnectSuccess'))
}

async function handleRespondCollab(id: number, accept: boolean) {
  await store.respondCollabRequest(id, accept)
  showToastMessage(accept ? t('creatorNetwork.acceptSuccess') : t('creatorNetwork.declineSuccess'))
}

onMounted(() => {
  store.fetchSummary()
  store.fetchCreators()
  store.fetchCollabRequests()
})
</script>
