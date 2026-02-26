<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('mediaKit.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('mediaKit.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('mediaKit.creditsRemaining') }}: {{ creditBalance }}
        </span>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="store.generating || !selectedStyle"
          @click="handleGenerate"
        >
          <SparklesIcon class="h-5 w-5" />
          {{ store.generating ? $t('mediaKit.generating') : $t('mediaKit.generateButton') }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('mediaKit.pageGuideTitle')"
      :items="($tm('mediaKit.pageGuide') as string[])"
    />

    <!-- Template Selector -->
    <div class="mb-6">
      <TemplateSelector
        :selected-style="selectedStyle"
        @select="selectedStyle = $event"
      />
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" full-page />

    <template v-else>
      <!-- Generating state -->
      <div
        v-if="store.generating"
        class="mb-6 flex flex-col items-center justify-center rounded-xl border border-gray-200 bg-white py-16 dark:border-gray-700 dark:bg-gray-900"
      >
        <LoadingSpinner />
        <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('mediaKit.generatingDesc') }}
        </p>
      </div>

      <!-- Kit List -->
      <div v-if="store.kits.length > 0" class="mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('mediaKit.myKits') }}
        </h2>

        <!-- Tabs: All / Published / Drafts -->
        <div class="mb-4 flex gap-4 border-b border-gray-200 dark:border-gray-700">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            :class="[
              'border-b-2 pb-3 text-sm font-medium transition-colors',
              activeTab === tab.key
                ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
            ]"
            @click="activeTab = tab.key"
          >
            {{ tab.label }} ({{ tab.count }})
          </button>
        </div>

        <!-- Kit Cards Grid -->
        <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <div
            v-for="kit in filteredKits"
            :key="kit.id"
            class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
            :class="selectedKit?.id === kit.id ? 'ring-2 ring-primary-500 dark:ring-primary-400' : ''"
            @click="selectedKit = kit"
          >
            <div class="mb-3 flex items-start justify-between">
              <div>
                <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ kit.title }}</h3>
                <span
                  class="mt-1 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="templateStyleBadge(kit.templateStyle)"
                >
                  {{ $t(`mediaKit.templateStyles.${kit.templateStyle}`) }}
                </span>
              </div>
              <span
                class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                :class="statusBadge(kit.status)"
              >
                {{ $t(`mediaKit.status.${kit.status}`) }}
              </span>
            </div>

            <p class="mb-3 line-clamp-2 text-sm text-gray-600 dark:text-gray-400">
              {{ kit.bio }}
            </p>

            <!-- Platform icons row -->
            <div class="mb-3 flex items-center gap-2">
              <span
                v-for="plat in kit.platforms"
                :key="plat.platform"
                class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-400"
              >
                {{ platformIcon(plat.platform) }} {{ formatNumber(plat.followers) }}
              </span>
            </div>

            <!-- Actions -->
            <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
              <span class="text-xs text-gray-400 dark:text-gray-500">
                {{ formatDate(kit.updatedAt) }}
              </span>
              <div class="flex items-center gap-2">
                <button
                  v-if="kit.status === 'DRAFT'"
                  class="rounded-lg px-2.5 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
                  @click.stop="handlePublish(kit.id)"
                >
                  {{ $t('mediaKit.publish') }}
                </button>
                <button
                  v-if="kit.status === 'PUBLISHED' && kit.publishedUrl"
                  class="rounded-lg px-2.5 py-1 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
                  @click.stop="copyLink(kit.publishedUrl!)"
                >
                  {{ $t('mediaKit.copyLink') }}
                </button>
                <button
                  class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                  @click.stop="handleDelete(kit.id)"
                >
                  <TrashIcon class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div
        v-else-if="!store.generating"
        class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
      >
        <DocumentTextIcon class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
        <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('mediaKit.noKits') }}</p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('mediaKit.noKitsDesc') }}</p>
      </div>

      <!-- Kit Detail / Preview -->
      <div v-if="selectedKit" class="mt-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('mediaKit.preview') }}
          </h2>
          <button
            class="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
            @click="selectedKit = null"
          >
            {{ $t('mediaKit.closePreview') }}
          </button>
        </div>

        <!-- Rate Card Editor -->
        <div class="mb-6">
          <RateCardEditor
            :rate-cards="selectedKit.rateCards"
            @update="handleRateCardsUpdate"
          />
        </div>

        <!-- Full Preview -->
        <MediaKitPreview :kit="selectedKit" />
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
import {
  SparklesIcon,
  TrashIcon,
  DocumentTextIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import TemplateSelector from '@/components/mediakit/TemplateSelector.vue'
import MediaKitPreview from '@/components/mediakit/MediaKitPreview.vue'
import RateCardEditor from '@/components/mediakit/RateCardEditor.vue'
import { useMediaKitStore } from '@/stores/mediaKit'
import { useCredit } from '@/composables/useCredit'
import type { MediaKit, RateCard } from '@/types/mediaKit'

const { t } = useI18n({ useScope: 'global' })
const store = useMediaKitStore()
const { balance: creditBalance, checkAndUse } = useCredit()

const selectedStyle = ref<string>('MODERN')
const selectedKit = ref<MediaKit | null>(null)
const activeTab = ref<'all' | 'published' | 'draft'>('all')
const showToast = ref(false)
const toastMessage = ref('')

const tabs = computed(() => [
  { key: 'all' as const, label: t('mediaKit.tabs.all'), count: store.kits.length },
  { key: 'published' as const, label: t('mediaKit.tabs.published'), count: store.publishedKits.length },
  { key: 'draft' as const, label: t('mediaKit.tabs.draft'), count: store.draftKits.length },
])

const filteredKits = computed(() => {
  if (activeTab.value === 'published') return store.publishedKits
  if (activeTab.value === 'draft') return store.draftKits
  return store.kits
})

function templateStyleBadge(style: string): string {
  const map: Record<string, string> = {
    MODERN: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    CLASSIC: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
    MINIMAL: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    CREATIVE: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  }
  return map[style] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

function statusBadge(status: string): string {
  const map: Record<string, string> = {
    DRAFT: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    PUBLISHED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

function platformIcon(platform: string): string {
  const icons: Record<string, string> = {
    youtube: '\u25B6\uFE0F',
    instagram: '\uD83D\uDCF7',
    tiktok: '\uD83C\uDFB5',
    naverclip: '\uD83D\uDCCE',
  }
  return icons[platform.toLowerCase()] || '\uD83C\uDF10'
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

let toastTimeout: ReturnType<typeof setTimeout> | null = null

function showToastMessage(message: string) {
  toastMessage.value = message
  showToast.value = true
  if (toastTimeout) clearTimeout(toastTimeout)
  toastTimeout = setTimeout(() => {
    showToast.value = false
  }, 3000)
}

async function handleGenerate() {
  if (!selectedStyle.value) return
  const canUse = await checkAndUse(5, t('mediaKit.title'))
  if (!canUse) return

  await store.generateKit(selectedStyle.value as MediaKit['templateStyle'])
  if (store.currentKit) {
    selectedKit.value = store.currentKit
  }
  showToastMessage(t('mediaKit.generateSuccess'))
}

async function handlePublish(id: number) {
  await store.publishKit(id)
  if (selectedKit.value?.id === id) {
    const updatedKit = store.kits.find(k => k.id === id)
    if (updatedKit) selectedKit.value = updatedKit
  }
  showToastMessage(t('mediaKit.publishSuccess'))
}

async function handleDelete(id: number) {
  if (!confirm(t('mediaKit.confirmDelete'))) return
  await store.deleteKit(id)
  if (selectedKit.value?.id === id) {
    selectedKit.value = null
  }
  showToastMessage(t('mediaKit.deleteSuccess'))
}

async function copyLink(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    showToastMessage(t('mediaKit.linkCopied'))
  } catch {
    showToastMessage(t('mediaKit.linkCopyFailed'))
  }
}

function handleRateCardsUpdate(rateCards: RateCard[]) {
  if (selectedKit.value) {
    selectedKit.value = { ...selectedKit.value, rateCards }
  }
}

onMounted(async () => {
  await Promise.all([store.fetchTemplates(), store.fetchKits()])
})
</script>
