<template>
  <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ funnel.videoTitle }}</h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ platformLabel(funnel.platform) }} &middot; 전환율 {{ funnel.overallConversion.toFixed(2) }}%
        </p>
      </div>
    </div>

    <!-- Funnel bars -->
    <div class="space-y-3">
      <FunnelBar
        v-for="(stage, i) in funnel.stages"
        :key="stage.name"
        :stage="stage"
        :index="i"
      />
    </div>

    <!-- Drop-off arrows between stages -->
    <div class="mt-4 flex items-center justify-center gap-2 text-xs text-gray-400 dark:text-gray-500">
      <span>노출</span>
      <template v-for="(stage, i) in funnel.stages.slice(1)" :key="'arrow-' + i">
        <span>&rarr;</span>
        <span>{{ stage.name }}</span>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { VideoFunnel } from '@/types/contentFunnel'
import FunnelBar from './FunnelBar.vue'

defineProps<{
  funnel: VideoFunnel
}>()

function platformLabel(platform: string): string {
  const labels: Record<string, string> = {
    youtube: 'YouTube',
    tiktok: 'TikTok',
    instagram: 'Instagram',
    naverclip: 'Naver Clip',
  }
  return labels[platform] ?? platform
}
</script>
