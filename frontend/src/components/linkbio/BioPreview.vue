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
            <!--
              프로필 이미지가 없으면 img 를 그리지 않는다. 빈 src 는 브라우저가 현재
              페이지를 다시 요청하게 만든다. 대신 로컬 아이콘을 그린다.
            -->
            <img
              v-if="page.avatarUrl"
              :src="page.avatarUrl"
              :alt="page.displayName"
              class="mb-4 h-24 w-24 rounded-full border-4 border-white shadow-lg dark:border-gray-700"
            />
            <span
              v-else
              role="img"
              :aria-label="page.displayName"
              class="mb-4 flex h-24 w-24 items-center justify-center rounded-full border-4 border-white bg-gray-200 shadow-lg dark:border-gray-700 dark:bg-gray-700"
            >
              <UserCircleIcon class="h-20 w-20 text-gray-400 dark:text-gray-500" />
            </span>
            <h1 class="mb-2 text-xl font-bold">{{ page.displayName }}</h1>
            <p class="text-sm opacity-80">{{ page.bio }}</p>
          </div>

          <!-- Blocks -->
          <div class="space-y-3">
            <template v-for="block in visibleBlocks" :key="block.id">
              <!--
                주소가 아직 없거나 유효하지 않으면 앵커를 만들지 않는다. 빈 href 는 현재
                페이지로 이동하고, `https://` 같은 값은 아무 데도 아닌 곳으로 보낸다.
                미리보기이므로 "주소 미입력" 을 그대로 보여주는 편이 정확하다.
              -->
              <template v-if="block.type === 'link'">
                <a
                  v-if="isValidLinkUrl(block.url)"
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
                <div
                  v-else
                  aria-disabled="true"
                  class="flex items-center justify-center gap-2 py-3 text-center font-medium opacity-50"
                  :class="getButtonClasses()"
                  :style="{
                    backgroundColor: page.buttonColor,
                    color: page.buttonTextColor,
                  }"
                >
                  <span v-if="block.icon">{{ block.icon }}</span>
                  <span>{{ block.title }}</span>
                  <span class="text-xs">(주소 미입력)</span>
                </div>
              </template>

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
                <!--
                  영상 링크가 아직 없으면 앵커를 만들지 않는다. 빈 href 는 현재 페이지로
                  이동한다. 썸네일도 없으면 로컬 placeholder 를 그린다 — 근거는
                  VideoBlock 타입 주석 참고.
                -->
                <component
                  :is="block.videoUrl ? 'a' : 'div'"
                  v-bind="block.videoUrl ? { href: block.videoUrl, target: '_blank', rel: 'noopener noreferrer' } : {}"
                >
                  <img
                    v-if="block.thumbnailUrl"
                    :src="block.thumbnailUrl"
                    :alt="block.title"
                    class="aspect-video w-full object-cover"
                  />
                  <div
                    v-else
                    role="img"
                    :aria-label="block.title"
                    class="flex aspect-video w-full items-center justify-center bg-gray-200 dark:bg-gray-700"
                  >
                    <PhotoIcon class="h-10 w-10 text-gray-400 dark:text-gray-500" />
                  </div>
                  <div class="p-3" :style="{ backgroundColor: page.buttonColor, color: page.buttonTextColor }">
                    <div class="text-sm font-medium">{{ block.title }}</div>
                  </div>
                </component>
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
import { PhotoIcon, UserCircleIcon } from '@heroicons/vue/24/outline'
import { isValidLinkUrl } from '@/types/linkbio'
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
