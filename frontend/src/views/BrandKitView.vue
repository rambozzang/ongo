<script setup lang="ts">
import { ref } from 'vue'
import { useBrandKitStore } from '@/stores/brandkit'
import { ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import ColorPalette from '@/components/brandkit/ColorPalette.vue'
import TypographySection from '@/components/brandkit/TypographySection.vue'
import AssetGrid from '@/components/brandkit/AssetGrid.vue'
import GuidelinesEditor from '@/components/brandkit/GuidelinesEditor.vue'
import type { BrandColor, BrandFont, BrandAsset } from '@/types/brandkit'

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

function handleSave() {
  brandKitStore.saveBrandKit()
  alert('브랜드 키트가 저장되었습니다.')
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
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="mb-8">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100">
              브랜드 키트
            </h1>
            <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
              모든 콘텐츠에 일관된 브랜드 아이덴티티를 적용하세요
            </p>
          </div>
          <button
            @click="handleSave"
            :disabled="!brandKitStore.isDirty"
            :class="[
              'px-6 py-2.5 rounded-lg font-medium transition-colors flex items-center gap-2',
              brandKitStore.isDirty
                ? 'bg-blue-600 hover:bg-blue-700 text-white'
                : 'bg-gray-300 dark:bg-gray-700 text-gray-500 dark:text-gray-400 cursor-not-allowed'
            ]"
          >
            <svg v-if="brandKitStore.isDirty" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>{{ brandKitStore.isDirty ? '변경사항 저장' : '저장됨' }}</span>
          </button>
        </div>
      </div>

      <!-- Sections -->
      <div class="space-y-6">
        <!-- Color Palette Section -->
        <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
          <button
            @click="toggleSection('colors')"
            class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div>
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
                색상 팔레트
              </h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                브랜드 색상을 정의하고 관리하세요
              </p>
            </div>
            <ChevronUpIcon v-if="sections.colors" class="w-6 h-6 text-gray-400" />
            <ChevronDownIcon v-else class="w-6 h-6 text-gray-400" />
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
        <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
          <button
            @click="toggleSection('typography')"
            class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div>
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
                서체
              </h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                브랜드 타이포그래피를 설정하세요
              </p>
            </div>
            <ChevronUpIcon v-if="sections.typography" class="w-6 h-6 text-gray-400" />
            <ChevronDownIcon v-else class="w-6 h-6 text-gray-400" />
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
        <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
          <button
            @click="toggleSection('assets')"
            class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div>
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
                브랜드 에셋
              </h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                로고, 워터마크, 템플릿 등을 관리하세요
              </p>
            </div>
            <ChevronUpIcon v-if="sections.assets" class="w-6 h-6 text-gray-400" />
            <ChevronDownIcon v-else class="w-6 h-6 text-gray-400" />
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
        <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
          <button
            @click="toggleSection('guidelines')"
            class="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div>
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
                브랜드 가이드라인
              </h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                브랜드 사용 규칙과 지침을 문서화하세요
              </p>
            </div>
            <ChevronUpIcon v-if="sections.guidelines" class="w-6 h-6 text-gray-400" />
            <ChevronDownIcon v-else class="w-6 h-6 text-gray-400" />
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
  </div>
</template>
