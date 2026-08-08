<template>
  <div class="overflow-hidden rounded-lg border border-line bg-surface-card text-content">
    <div class="flex items-center gap-2 border-b border-line px-3 py-2.5">
      <span class="h-2.5 w-2.5 rounded-full" :style="{ backgroundColor: platformConfig.color }" aria-hidden="true" />
      <span class="text-[12px] font-semibold">{{ platformConfig.label }}</span>
    </div>
    <div class="relative aspect-[4/5] bg-surface-input">
      <img v-if="thumbnail" :src="thumbnail" :alt="t('preview.thumbnailAlt')" class="h-full w-full object-cover" />
      <div v-else class="flex h-full items-center justify-center text-content-tertiary">
        {{ t('preview.noThumbnail') }}
      </div>
      <div class="absolute inset-x-0 bottom-0 bg-black/75 p-3 text-white">
        <p class="line-clamp-3 text-[12px] font-semibold">{{ title || t('preview.enterTitle') }}</p>
        <p v-if="description" class="mt-1 line-clamp-3 text-[10.5px] text-white/80">{{ description }}</p>
        <p v-if="tags.length" class="mt-1 line-clamp-2 text-[10px] text-accent">{{ tags.map((tag) => `#${tag.replace(/^#/, '')}`).join(' ') }}</p>
      </div>
    </div>
    <div class="flex items-center justify-between border-t border-line px-3 py-2 text-[10px] text-content-tertiary">
      <span>{{ channelName || t('preview.channelName') }}</span>
      <span>{{ t('preview.draftPreview') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

const { t } = useI18n({ useScope: 'global' })

const props = withDefaults(defineProps<{
  platform: Platform
  title?: string
  description?: string
  thumbnail?: string
  channelName?: string
  tags?: string[]
}>(), {
  title: '',
  description: '',
  thumbnail: '',
  channelName: '',
  tags: () => [],
})

const platformConfig = computed(() => PLATFORM_CONFIG[props.platform])
</script>
