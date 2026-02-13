<template>
  <div class="flex justify-center">
    <!-- Phone Frame -->
    <div class="relative h-[700px] w-[375px] overflow-hidden rounded-[3rem] border-8 border-gray-800 bg-gray-800 shadow-2xl dark:border-gray-700">
      <!-- Notch -->
      <div class="absolute left-1/2 top-0 z-10 h-6 w-40 -translate-x-1/2 rounded-b-2xl bg-gray-800 dark:bg-gray-700"></div>

      <!-- Screen -->
      <div
        class="h-full w-full overflow-y-auto"
        :style="{
          backgroundColor: page.backgroundColor,
          color: page.textColor,
        }"
      >
        <div class="px-6 py-8 pb-12">
          <!-- Profile Section -->
          <div class="mb-6 flex flex-col items-center text-center">
            <img
              :src="page.avatarUrl"
              :alt="page.displayName"
              class="mb-4 h-24 w-24 rounded-full border-4 border-white shadow-lg dark:border-gray-700"
            />
            <h1 class="mb-2 text-xl font-bold">{{ page.displayName }}</h1>
            <p class="text-sm opacity-80">{{ page.bio }}</p>
          </div>

          <!-- Blocks -->
          <div class="space-y-3">
            <template v-for="block in visibleBlocks" :key="block.id">
              <!-- Link Block -->
              <a
                v-if="block.type === 'link'"
                :href="block.url"
                target="_blank"
                rel="noopener noreferrer"
                class="flex items-center justify-center gap-2 py-3 text-center font-medium transition-all"
                :class="getButtonClasses()"
                :style="{
                  backgroundColor: page.buttonColor,
                  color: page.buttonTextColor,
                }"
              >
                <span v-if="block.icon">{{ block.icon }}</span>
                <span>{{ block.title }}</span>
              </a>

              <!-- Header Block -->
              <div
                v-else-if="block.type === 'header'"
                class="pt-2 text-lg font-bold"
              >
                {{ block.text }}
              </div>

              <!-- Social Block -->
              <div
                v-else-if="block.type === 'social'"
                class="flex justify-center"
              >
                <a
                  :href="block.url"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="inline-flex h-12 w-12 items-center justify-center rounded-full transition-all"
                  :style="{
                    backgroundColor: page.buttonColor,
                    color: page.buttonTextColor,
                  }"
                >
                  <span class="text-xl">{{ getSocialIcon(block.platform) }}</span>
                </a>
              </div>

              <!-- Video Block -->
              <div
                v-else-if="block.type === 'video'"
                class="overflow-hidden rounded-xl shadow-md"
              >
                <a :href="block.videoUrl" target="_blank" rel="noopener noreferrer">
                  <img
                    :src="block.thumbnailUrl"
                    :alt="block.title"
                    class="aspect-video w-full object-cover"
                  />
                  <div class="p-3" :style="{ backgroundColor: page.buttonColor, color: page.buttonTextColor }">
                    <div class="text-sm font-medium">{{ block.title }}</div>
                  </div>
                </a>
              </div>

              <!-- Divider Block -->
              <hr
                v-else-if="block.type === 'divider'"
                class="my-4 border-t-2 opacity-20"
                :style="{ borderColor: page.textColor }"
              />

              <!-- Text Block -->
              <div
                v-else-if="block.type === 'text'"
                class="rounded-lg bg-black bg-opacity-5 p-3 text-center text-sm dark:bg-white dark:bg-opacity-5"
              >
                {{ block.content }}
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BioPage } from '@/types/linkbio'

const props = defineProps<{
  page: BioPage
}>()

const visibleBlocks = computed(() => {
  return props.page.blocks.filter(block => block.isVisible)
})

const getButtonClasses = () => {
  switch (props.page.theme) {
    case 'rounded':
      return 'rounded-full shadow-md hover:shadow-lg'
    case 'gradient':
      return 'rounded-lg shadow-md hover:shadow-lg'
    case 'minimal':
      return 'rounded-md border border-current hover:opacity-80'
    case 'dark':
      return 'rounded-lg shadow-md hover:shadow-lg'
    case 'colorful':
      return 'rounded-2xl shadow-lg hover:scale-105'
    default:
      return 'rounded-lg hover:opacity-90'
  }
}

const getSocialIcon = (platform: string): string => {
  switch (platform) {
    case 'instagram':
      return '📷'
    case 'twitter':
      return '🐦'
    case 'youtube':
      return '▶️'
    case 'tiktok':
      return '🎵'
    case 'facebook':
      return '👍'
    default:
      return '🔗'
  }
}
</script>
