<script setup lang="ts">
import { computed } from 'vue'
import type { Watermark } from '@/types/watermark'

const props = defineProps<{
  watermark: Watermark
  platformName?: string
}>()

const positionStyles = computed(() => {
  const pos = props.watermark.position
  const margin = `${props.watermark.margin}px`
  const style: Record<string, string> = {
    position: 'absolute',
  }

  // Vertical
  if (pos.startsWith('top')) {
    style.top = margin
  } else if (pos.startsWith('bottom')) {
    style.bottom = margin
  } else {
    style.top = '50%'
    style.transform = 'translateY(-50%)'
  }

  // Horizontal
  if (pos.endsWith('left')) {
    style.left = margin
  } else if (pos.endsWith('right')) {
    style.right = margin
  } else {
    // center horizontally
    if (style.transform) {
      style.left = '50%'
      style.transform = 'translate(-50%, -50%)'
    } else {
      style.left = '50%'
      style.transform = 'translateX(-50%)'
    }
  }

  return style
})

const watermarkStyle = computed(() => {
  const wm = props.watermark
  const base: Record<string, string> = {
    ...positionStyles.value,
    opacity: String(wm.opacity / 100),
    pointerEvents: 'none',
  }

  if (wm.type === 'text') {
    base.fontSize = `${wm.fontSize}px`
    base.fontFamily = wm.fontFamily
    base.color = wm.color
    base.fontWeight = wm.bold ? '700' : '400'
    base.fontStyle = wm.italic ? 'italic' : 'normal'
    base.whiteSpace = 'nowrap'
    base.textShadow = '0 1px 3px rgba(0, 0, 0, 0.5)'
    base.lineHeight = '1.2'
  } else {
    base.width = `${wm.size}%`
    base.height = 'auto'
  }

  return base
})

const hasContent = computed(() => {
  if (props.watermark.type === 'text') {
    return props.watermark.text.trim().length > 0
  }
  return props.watermark.imageUrl.length > 0
})
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between">
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
        미리보기
      </label>
      <span
        v-if="platformName"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300"
      >
        {{ platformName }}
      </span>
    </div>
    <div
      class="relative w-full overflow-hidden rounded-lg border border-gray-200 dark:border-gray-600"
      style="aspect-ratio: 16 / 9"
    >
      <!-- Simulated video frame -->
      <div
        class="absolute inset-0"
        style="background: linear-gradient(135deg, #374151 0%, #1f2937 40%, #111827 100%)"
      >
        <!-- Grid overlay to simulate video content -->
        <div class="absolute inset-0 opacity-10">
          <div class="h-full w-full" style="background-image: repeating-linear-gradient(0deg, transparent, transparent 30px, rgba(255,255,255,0.1) 30px, rgba(255,255,255,0.1) 31px), repeating-linear-gradient(90deg, transparent, transparent 30px, rgba(255,255,255,0.1) 30px, rgba(255,255,255,0.1) 31px)"></div>
        </div>

        <!-- Play button icon in center -->
        <div class="absolute inset-0 flex items-center justify-center">
          <div class="w-16 h-16 rounded-full bg-white/10 flex items-center justify-center">
            <svg class="w-8 h-8 text-white/30 ml-1" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </div>
        </div>

        <!-- Watermark overlay -->
        <template v-if="hasContent">
          <span
            v-if="props.watermark.type === 'text'"
            :style="watermarkStyle"
          >
            {{ props.watermark.text }}
          </span>
          <img
            v-else
            :src="props.watermark.imageUrl"
            :alt="props.watermark.fileName"
            :style="watermarkStyle"
            class="object-contain"
          />
        </template>

        <!-- Empty state -->
        <div
          v-if="!hasContent"
          class="absolute inset-0 flex items-center justify-center"
        >
          <p class="text-white/40 text-sm">워터마크를 설정하면 여기에 미리보기가 표시됩니다</p>
        </div>
      </div>
    </div>
  </div>
</template>
