<template>
  <div class="flex flex-col desktop:flex-row gap-6">
    <!-- Mobile TOC Toggle -->
    <button
      type="button"
      class="flex items-center gap-2 rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 dark:border-gray-700 dark:text-gray-300 desktop:hidden"
      @click="showToc = !showToc"
    >
      <ListBulletIcon class="h-5 w-5" />
      {{ t('manual.toc') }}
      <ChevronDownIcon
        class="ml-auto h-4 w-4 transition-transform"
        :class="showToc ? 'rotate-180' : ''"
      />
    </button>

    <!-- Table of Contents Sidebar -->
    <nav
      v-show="showToc || !isMobile"
      class="w-full shrink-0 desktop:w-56"
    >
      <div class="sticky top-6 rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ t('manual.toc') }}
        </h2>
        <ul class="space-y-1">
          <li v-for="section in sections" :key="section.id">
            <a
              :href="'#' + section.id"
              class="block rounded px-2 py-1.5 text-sm transition-colors"
              :class="
                activeSection === section.id
                  ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-100'
              "
              @click="showToc = false"
            >
              {{ section.title }}
            </a>
          </li>
        </ul>
      </div>
    </nav>

    <!-- Manual Content -->
    <div class="min-w-0 flex-1">
      <h1 class="mb-6 text-2xl font-bold text-gray-900 dark:text-gray-100">
        {{ t('manual.title') }}
      </h1>

      <div class="space-y-10">
        <section
          v-for="section in sections"
          :id="section.id"
          :key="section.id"
          class="scroll-mt-6"
        >
          <div class="card">
            <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
              <component :is="section.icon" class="h-5 w-5 text-primary-600" />
              {{ section.title }}
            </h2>
            <div class="prose prose-sm max-w-none text-gray-700 dark:text-gray-300">
              <div v-for="(block, i) in section.content" :key="i" class="mb-4 last:mb-0">
                <h3
                  v-if="block.subtitle"
                  class="mb-2 text-sm font-semibold text-gray-800 dark:text-gray-200"
                >
                  {{ block.subtitle }}
                </h3>
                <p class="text-sm leading-relaxed whitespace-pre-line">{{ block.text }}</p>
                <ol
                  v-if="block.steps"
                  class="mt-2 list-decimal space-y-1 pl-5 text-sm"
                >
                  <li v-for="(step, j) in block.steps" :key="j">{{ step }}</li>
                </ol>
                <ul
                  v-if="block.items"
                  class="mt-2 list-disc space-y-1 pl-5 text-sm"
                >
                  <li v-for="(item, j) in block.items" :key="j">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  ListBulletIcon,
  ChevronDownIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { sectionsKo, sectionsEn } from '@/components/manual/manualSections'
import type { ManualSection } from '@/components/manual/manualSections'

const { isKorean, t } = useLocale()

const showToc = ref(false)
const isMobile = ref(false)
const activeSection = ref('getting-started')

const sections = computed<ManualSection[]>(() =>
  isKorean.value ? sectionsKo : sectionsEn,
)

// Intersection Observer for active section tracking
let observer: IntersectionObserver | null = null

function checkMobile() {
  isMobile.value = window.innerWidth < 1024
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeSection.value = entry.target.id
        }
      }
    },
    { rootMargin: '-20% 0px -60% 0px' },
  )

  setTimeout(() => {
    const sectionElements = document.querySelectorAll('section[id]')
    sectionElements.forEach((el) => observer?.observe(el))
  }, 100)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  observer?.disconnect()
})
</script>
