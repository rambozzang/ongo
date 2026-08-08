<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBrandKitStore } from '@/stores/brandkit'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import ColorPalette from '@/components/brandkit/ColorPalette.vue'
import TypographySection from '@/components/brandkit/TypographySection.vue'
import AssetGrid from '@/components/brandkit/AssetGrid.vue'
import GuidelinesEditor from '@/components/brandkit/GuidelinesEditor.vue'
import type { BrandColor, BrandFont, BrandAsset } from '@/types/brandkit'
import { useNotification } from '@/composables/useNotification'

const { t } = useI18n()
const notify = useNotification()
const brandKitStore = useBrandKitStore()

const sections = ref({
  colors: true,
  typography: true,
  assets: true,
  guidelines: true,
})

function toggleSection(section: keyof typeof sections.value) {
  sections.value[section] = !sections.value[section]
}

async function handleSave() {
  try {
    await brandKitStore.saveBrandKit()
  } catch {
    return
  }
  notify.success(t('brandKit.saveSuccess'))
}

// Color handlers
function handleAddColor(color: Omit<BrandColor, 'id'>) {
  brandKitStore.addColor(color)
}

function handleUpdateColor(id: number, updates: Partial<BrandColor>) {
  brandKitStore.updateColor(id, updates)
}

function handleRemoveColor(id: number) {
  brandKitStore.removeColor(id)
}

// Font handlers
function handleAddFont(font: Omit<BrandFont, 'id'>) {
  brandKitStore.addFont(font)
}

function handleUpdateFont(id: number, updates: Partial<BrandFont>) {
  brandKitStore.updateFont(id, updates)
}

function handleRemoveFont(id: number) {
  brandKitStore.removeFont(id)
}

// Asset handlers
function handleAddAsset(asset: Omit<BrandAsset, 'id'>) {
  brandKitStore.addAsset(asset)
}

function handleRemoveAsset(id: number) {
  brandKitStore.removeAsset(id)
}

// Guidelines handler
function handleUpdateGuidelines(text: string) {
  brandKitStore.updateGuidelines(text)
}

onMounted(() => {
  brandKitStore.fetchBrandKits().catch(() => undefined)
})
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('brandKit.title')" :description="$t('brandKit.description')">
      <template #actions>
        <button
          :disabled="!brandKitStore.isDirty"
          :class="[
            'btn-primary inline-flex items-center gap-2',
            !brandKitStore.isDirty && 'opacity-50 cursor-not-allowed'
          ]"
          @click="handleSave"
        >
          <svg v-if="brandKitStore.isDirty" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <span>{{ brandKitStore.isDirty ? $t('brandKit.saveChanges') : $t('brandKit.saved') }}</span>
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('brandKit.pageGuideTitle')" :items="($tm('brandKit.pageGuide') as string[])" />

    <!-- Sections -->
    <div class="space-y-6">
      <!-- Color Palette Section -->
      <section class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
        <button
          class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          @click="toggleSection('colors')"
        >
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('brandKit.colorPalette') }}
            </h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
              {{ $t('brandKit.colorPaletteDesc') }}
            </p>
          </div>
          <ChevronUpIcon v-if="sections.colors" class="h-5 w-5 text-gray-400" />
          <ChevronDownIcon v-else class="h-5 w-5 text-gray-400" />
        </button>
        <div v-if="sections.colors" class="px-6 pb-6 border-t border-gray-200 dark:border-gray-700">
          <div class="pt-6">
            <ColorPalette
              :colors="brandKitStore.brandKit.colors"
              @add="handleAddColor"
              @update="handleUpdateColor"
              @remove="handleRemoveColor"
            />
          </div>
        </div>
      </section>

      <!-- Typography Section -->
      <section class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
        <button
          class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          @click="toggleSection('typography')"
        >
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('brandKit.typography') }}
            </h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
              {{ $t('brandKit.typographyDesc') }}
            </p>
          </div>
          <ChevronUpIcon v-if="sections.typography" class="h-5 w-5 text-gray-400" />
          <ChevronDownIcon v-else class="h-5 w-5 text-gray-400" />
        </button>
        <div v-if="sections.typography" class="px-6 pb-6 border-t border-gray-200 dark:border-gray-700">
          <div class="pt-6">
            <TypographySection
              :fonts="brandKitStore.brandKit.fonts"
              @add="handleAddFont"
              @update="handleUpdateFont"
              @remove="handleRemoveFont"
            />
          </div>
        </div>
      </section>

      <!-- Brand Assets Section -->
      <section class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
        <button
          class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          @click="toggleSection('assets')"
        >
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('brandKit.brandAssets') }}
            </h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
              {{ $t('brandKit.brandAssetsDesc') }}
            </p>
          </div>
          <ChevronUpIcon v-if="sections.assets" class="h-5 w-5 text-gray-400" />
          <ChevronDownIcon v-else class="h-5 w-5 text-gray-400" />
        </button>
        <div v-if="sections.assets" class="px-6 pb-6 border-t border-gray-200 dark:border-gray-700">
          <div class="pt-6">
            <AssetGrid
              :assets="brandKitStore.brandKit.assets"
              @add="handleAddAsset"
              @remove="handleRemoveAsset"
            />
          </div>
        </div>
      </section>

      <!-- Brand Guidelines Section -->
      <section class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
        <button
          class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          @click="toggleSection('guidelines')"
        >
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('brandKit.guidelines') }}
            </h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
              {{ $t('brandKit.guidelinesDesc') }}
            </p>
          </div>
          <ChevronUpIcon v-if="sections.guidelines" class="h-5 w-5 text-gray-400" />
          <ChevronDownIcon v-else class="h-5 w-5 text-gray-400" />
        </button>
        <div v-if="sections.guidelines" class="px-6 pb-6 border-t border-gray-200 dark:border-gray-700">
          <div class="pt-6">
            <GuidelinesEditor
              :model-value="brandKitStore.brandKit.guidelines"
              @update:model-value="handleUpdateGuidelines"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
