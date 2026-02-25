<template>
  <div class="relative">
    <!-- Loading -->
    <div v-if="store.loading" class="flex min-h-[400px] items-center justify-center">
      <div class="text-center">
        <div class="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
        <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">{{ t('portfolio.loading') }}</p>
      </div>
    </div>

    <template v-else-if="portfolio">
      <!-- Header -->
      <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('portfolio.title') }}</h1>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ t('portfolio.description') }}
          </p>
        </div>
        <div class="flex items-center gap-3">
          <!-- Public URL -->
          <div v-if="portfolio.isPublic" class="hidden items-center gap-2 rounded-md border border-gray-300 bg-gray-50 px-3 py-2 dark:border-gray-600 dark:bg-gray-700 sm:flex">
            <span class="max-w-[200px] truncate text-sm text-gray-600 dark:text-gray-400">{{ portfolio.publicUrl }}</span>
            <button
              class="text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
              @click="copyLink"
            >
              <ClipboardDocumentIcon class="h-5 w-5" />
            </button>
          </div>
          <!-- Save Button -->
          <button
            :disabled="!store.isDirty"
            :class="[
              'btn-primary inline-flex items-center gap-2',
              !store.isDirty && 'cursor-not-allowed opacity-50',
            ]"
            @click="handleSave"
          >
            <CheckIcon class="h-5 w-5" />
            {{ store.isDirty ? t('portfolio.saveChanges') : t('portfolio.saved') }}
          </button>
        </div>
      </div>

      <PageGuide :title="t('portfolio.pageGuideTitle')" :items="(tm('portfolio.pageGuide') as string[])" />

      <!-- Summary Stats Bar -->
      <div class="mb-6 grid grid-cols-3 gap-4">
        <div class="card p-3">
          <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.totalSubscribers') }}</div>
          <div class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatNumber(store.totalSubscribers) }}
          </div>
        </div>
        <div class="card p-3">
          <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.totalViewsAll') }}</div>
          <div class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatNumber(store.totalViews) }}
          </div>
        </div>
        <div class="card p-3">
          <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.avgEngagement') }}</div>
          <div class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.avgEngagement }}%
          </div>
        </div>
      </div>

      <!-- Mobile Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700 lg:hidden">
        <div class="flex gap-4">
          <button
            @click="activeTab = 'editor'"
            :class="[
              'border-b-2 py-3 text-sm font-medium transition-colors',
              activeTab === 'editor'
                ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
            ]"
          >
            {{ t('portfolio.tabEditor') }}
          </button>
          <button
            @click="activeTab = 'preview'"
            :class="[
              'border-b-2 py-3 text-sm font-medium transition-colors',
              activeTab === 'preview'
                ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
            ]"
          >
            {{ t('portfolio.tabPreview') }}
          </button>
        </div>
      </div>

      <!-- Main Content -->
      <div class="flex flex-col gap-8 lg:flex-row">
        <!-- Editor Panel -->
        <div
          :class="[
            'flex-1 space-y-6',
            activeTab === 'preview' ? 'hidden lg:block' : 'block',
          ]"
        >
          <PortfolioHeader
            :profile="portfolio.profile"
            @update="handleProfileUpdate"
          />

          <PortfolioStats :stats="portfolio.channelStats" />

          <PortfolioShowcase
            :contents="portfolio.showcaseContents"
            @reorder="handleReorderShowcase"
            @remove="handleRemoveShowcase"
          />

          <PortfolioBrandHistory
            :collaborations="portfolio.brandCollaborations"
            @remove="handleRemoveCollab"
          />

          <MediaKitExport
            :portfolio="portfolio"
            @update-theme="handleUpdateTheme"
            @update-slug="handleUpdateSlug"
            @toggle-public="handleTogglePublic"
            @export-pdf="handleExportPdf"
            @copy-link="copyLink"
          />
        </div>

        <!-- Preview Panel -->
        <div
          :class="[
            'lg:w-[400px]',
            activeTab === 'editor' ? 'hidden lg:block' : 'block',
          ]"
        >
          <div class="sticky top-8">
            <PortfolioPreview :portfolio="portfolio" />
          </div>
        </div>
      </div>
    </template>

    <!-- Toast Notification -->
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ClipboardDocumentIcon, CheckIcon } from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import PortfolioHeader from '@/components/portfolio/PortfolioHeader.vue'
import PortfolioStats from '@/components/portfolio/PortfolioStats.vue'
import PortfolioShowcase from '@/components/portfolio/PortfolioShowcase.vue'
import PortfolioBrandHistory from '@/components/portfolio/PortfolioBrandHistory.vue'
import MediaKitExport from '@/components/portfolio/MediaKitExport.vue'
import PortfolioPreview from '@/components/portfolio/PortfolioPreview.vue'
import type { PortfolioProfile, PortfolioTheme } from '@/types/portfolio'

const { t, tm } = useI18n({ useScope: 'global' })
const store = usePortfolioStore()
const { portfolio } = storeToRefs(store)

const activeTab = ref<'editor' | 'preview'>('editor')
const showToast = ref(false)
const toastMessage = ref('')

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}

function handleProfileUpdate(updates: Partial<PortfolioProfile>) {
  store.updateProfile(updates)
}

function handleReorderShowcase(fromIndex: number, toIndex: number) {
  store.reorderShowcase(fromIndex, toIndex)
}

function handleRemoveShowcase(contentId: number) {
  store.removeShowcaseContent(contentId)
}

function handleRemoveCollab(collabId: number) {
  store.removeBrandCollaboration(collabId)
}

function handleUpdateTheme(theme: PortfolioTheme) {
  store.updateTheme(theme)
}

function handleUpdateSlug(slug: string) {
  store.updateSlug(slug)
}

function handleTogglePublic() {
  store.togglePublic()
}

async function handleExportPdf() {
  try {
    const { jsPDF } = await import('jspdf')
    const doc = new jsPDF()
    const p = portfolio.value
    if (!p) return

    doc.setFontSize(24)
    doc.text(p.profile.displayName, 20, 30)

    doc.setFontSize(12)
    doc.text(p.profile.bio, 20, 45, { maxWidth: 170 })

    doc.setFontSize(14)
    doc.text('Channel Statistics', 20, 70)

    let y = 80
    doc.setFontSize(10)
    for (const stat of p.channelStats) {
      doc.text(
        `${stat.channelName} (${stat.platform}) - Subscribers: ${stat.subscribers.toLocaleString()} | Views: ${stat.totalViews.toLocaleString()} | Engagement: ${stat.engagementRate}%`,
        20,
        y,
        { maxWidth: 170 },
      )
      y += 10
    }

    y += 10
    doc.setFontSize(14)
    doc.text('Showcase Contents', 20, y)
    y += 10
    doc.setFontSize(10)
    for (const content of p.showcaseContents) {
      doc.text(
        `${content.title} - ${content.viewCount.toLocaleString()} views`,
        20,
        y,
        { maxWidth: 170 },
      )
      y += 8
    }

    y += 10
    doc.setFontSize(14)
    doc.text('Brand Collaborations', 20, y)
    y += 10
    doc.setFontSize(10)
    for (const collab of p.brandCollaborations) {
      doc.text(
        `${collab.brandName} - ${collab.campaignTitle} (${collab.resultSummary})`,
        20,
        y,
        { maxWidth: 170 },
      )
      y += 8
    }

    doc.save(`${p.profile.displayName}-media-kit.pdf`)
    showToastMessage(t('portfolio.exportSuccess'))
  } catch {
    showToastMessage(t('portfolio.exportFailed'))
  }
}

async function copyLink() {
  if (!portfolio.value) return
  try {
    await navigator.clipboard.writeText(portfolio.value.publicUrl)
    showToastMessage(t('portfolio.linkCopied'))
  } catch {
    showToastMessage(t('portfolio.linkCopyFailed'))
  }
}

function handleSave() {
  store.savePortfolio()
  showToastMessage(t('portfolio.savedMessage'))
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

onMounted(() => {
  store.fetchPortfolio()
})

onUnmounted(() => {
  if (toastTimeout) clearTimeout(toastTimeout)
})
</script>
