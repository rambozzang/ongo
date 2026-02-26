<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('influencerMatch.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('influencerMatch.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('influencerMatch.creditsRemaining') }}: {{ creditBalance }}
        </span>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="searching"
          @click="handleFindMatches"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ searching ? $t('influencerMatch.searching') : $t('influencerMatch.findMatches') }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('influencerMatch.pageGuideTitle')"
      :items="($tm('influencerMatch.pageGuide') as string[])"
    />

    <!-- Tabs -->
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

    <!-- Matches Tab -->
    <div v-if="activeTab === 'matches'">
      <!-- Filter Bar -->
      <div class="mb-4 flex flex-wrap gap-3">
        <select
          v-model="filter.category"
          class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
        >
          <option value="">{{ $t('influencerMatch.filter.allCategories') }}</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>

        <select
          v-model="filter.platform"
          class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
        >
          <option value="">{{ $t('influencerMatch.filter.allPlatforms') }}</option>
          <option value="youtube">YouTube</option>
          <option value="instagram">Instagram</option>
          <option value="tiktok">TikTok</option>
          <option value="naverclip">Naver Clip</option>
        </select>

        <select
          v-model="followerRange"
          class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
        >
          <option value="">{{ $t('influencerMatch.filter.allFollowers') }}</option>
          <option value="micro">1K - 10K</option>
          <option value="mid">10K - 100K</option>
          <option value="macro">100K - 1M</option>
          <option value="mega">1M+</option>
        </select>

        <input
          v-model.number="filter.minEngagement"
          type="number"
          min="0"
          max="100"
          step="0.1"
          :placeholder="$t('influencerMatch.filter.minEngagement')"
          class="w-36 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
        />
      </div>

      <!-- Loading -->
      <div v-if="searching" class="flex items-center justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>

      <!-- Empty State -->
      <div v-else-if="influencers.length === 0" class="py-12 text-center">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <UserGroupIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('influencerMatch.emptyMatches') }}
        </h3>
        <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('influencerMatch.emptyMatchesDesc') }}
        </p>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="searching"
          @click="handleFindMatches"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('influencerMatch.findMatches') }}
        </button>
      </div>

      <!-- Influencer Grid -->
      <div v-else class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <InfluencerProfileCard
          v-for="profile in filteredInfluencers"
          :key="profile.id"
          :profile="profile"
          @contact="openCollabModal"
        />
      </div>
    </div>

    <!-- Collaborations Tab -->
    <div v-if="activeTab === 'collabs'">
      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>

      <!-- Empty State -->
      <div v-else-if="collabs.length === 0" class="py-12 text-center">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <ChatBubbleLeftRightIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('influencerMatch.emptyCollabs') }}
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ $t('influencerMatch.emptyCollabsDesc') }}
        </p>
      </div>

      <!-- Collab Lists -->
      <div v-else class="space-y-6">
        <!-- Active Collabs -->
        <div v-if="activeCollabs.length > 0">
          <h3 class="mb-3 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('influencerMatch.activeCollabs') }}
          </h3>
          <div class="space-y-3">
            <CollabRequestCard
              v-for="collab in activeCollabs"
              :key="collab.id"
              :collab="collab"
            />
          </div>
        </div>

        <!-- Past Collabs -->
        <div v-if="pastCollabs.length > 0">
          <h3 class="mb-3 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('influencerMatch.pastCollabs') }}
          </h3>
          <div class="space-y-3">
            <CollabRequestCard
              v-for="collab in pastCollabs"
              :key="collab.id"
              :collab="collab"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Send Collab Modal -->
    <SendCollabModal
      v-model="showCollabModal"
      :influencer-name="selectedInfluencerName"
      @send="handleSendCollab"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  SparklesIcon,
  UserGroupIcon,
  ChatBubbleLeftRightIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import InfluencerProfileCard from '@/components/influencermatch/InfluencerProfileCard.vue'
import CollabRequestCard from '@/components/influencermatch/CollabRequestCard.vue'
import SendCollabModal from '@/components/influencermatch/SendCollabModal.vue'
import { useInfluencerMatchStore } from '@/stores/influencerMatch'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { MatchFilter } from '@/types/influencerMatch'

const { t } = useI18n({ useScope: 'global' })
const store = useInfluencerMatchStore()
const { balance: creditBalance, checkAndUse } = useCredit()
const notification = useNotification()

const {
  influencers,
  collabs,
  loading,
  searching,
  activeCollabs,
} = storeToRefs(store)

const activeTab = ref<'matches' | 'collabs'>('matches')
const showCollabModal = ref(false)
const selectedInfluencerId = ref<number | null>(null)
const selectedInfluencerName = ref('')
const followerRange = ref('')

const filter = reactive<MatchFilter>({
  category: undefined,
  platform: undefined,
  minFollowers: undefined,
  maxFollowers: undefined,
  minEngagement: undefined,
  maxBudget: undefined,
})

const categories = ['음식', '여행', '뷰티', '테크', '피트니스', '게임', '음악', '교육', '패션', '일상']

const tabs = computed(() => [
  { key: 'matches' as const, label: t('influencerMatch.tabs.matches') },
  { key: 'collabs' as const, label: t('influencerMatch.tabs.collabs') },
])

const pastCollabs = computed(() =>
  collabs.value.filter((c) => ['COMPLETED', 'DECLINED'].includes(c.status)),
)

const filteredInfluencers = computed(() => {
  let result = influencers.value

  if (filter.category) {
    result = result.filter((i) => i.category === filter.category)
  }
  if (filter.platform) {
    result = result.filter((i) => i.platform === filter.platform)
  }
  if (filter.minEngagement) {
    result = result.filter((i) => i.engagementRate >= (filter.minEngagement ?? 0))
  }

  if (followerRange.value) {
    const ranges: Record<string, [number, number]> = {
      micro: [1000, 10000],
      mid: [10000, 100000],
      macro: [100000, 1000000],
      mega: [1000000, Infinity],
    }
    const [min, max] = ranges[followerRange.value] ?? [0, Infinity]
    result = result.filter((i) => i.followers >= min && i.followers < max)
  }

  return result
})

function openCollabModal(influencerId: number) {
  const inf = influencers.value.find((i) => i.id === influencerId)
  if (!inf) return
  selectedInfluencerId.value = influencerId
  selectedInfluencerName.value = inf.name
  showCollabModal.value = true
}

async function handleFindMatches() {
  const canUse = await checkAndUse(5, t('influencerMatch.title'))
  if (!canUse) return

  try {
    const appliedFilter: MatchFilter = {}
    if (filter.category) appliedFilter.category = filter.category
    if (filter.platform) appliedFilter.platform = filter.platform
    if (filter.minEngagement) appliedFilter.minEngagement = filter.minEngagement
    if (followerRange.value) {
      const ranges: Record<string, [number, number]> = {
        micro: [1000, 10000],
        mid: [10000, 100000],
        macro: [100000, 1000000],
        mega: [1000000, Infinity],
      }
      const [min, max] = ranges[followerRange.value] ?? [0, Infinity]
      appliedFilter.minFollowers = min
      if (max !== Infinity) appliedFilter.maxFollowers = max
    }

    await store.findMatches(Object.keys(appliedFilter).length > 0 ? appliedFilter : undefined)
  } catch {
    notification.error(t('influencerMatch.findError'))
  }
}

async function handleSendCollab(payload: { message: string; budget: number }) {
  if (!selectedInfluencerId.value) return

  try {
    await store.sendCollab(selectedInfluencerId.value, payload.message, payload.budget)
    showCollabModal.value = false
    notification.success(t('influencerMatch.collabSent'))
  } catch {
    notification.error(t('influencerMatch.collabError'))
  }
}

onMounted(async () => {
  await Promise.all([store.findMatches(), store.fetchCollabs()])
})
</script>
