<template>
  <div class="mb-4">
    <button
      class="flex w-full items-center gap-3 rounded-[11px] border border-line-control bg-surface-card px-4 py-3 text-body-sm text-content-secondary transition-colors hover:border-line-hover hover:bg-surface-raised"
      @click="toggle"
    >
      <InformationCircleIcon class="h-5 w-5 flex-shrink-0 text-accent" />
      <span class="font-semibold">{{ title }}</span>
      <span class="text-body-xs text-content-tertiary">{{ isOpen ? t('common.collapse') : t('common.showMore') }}</span>
      <ChevronUpIcon v-if="isOpen" class="ml-auto h-4 w-4" />
      <ChevronDownIcon v-else class="ml-auto h-4 w-4" />
    </button>
    <div
      class="grid transition-all duration-300 ease-in-out"
      :class="isOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'"
    >
      <div class="overflow-hidden">
        <ul class="mt-2 space-y-1.5 rounded-[11px] border border-line bg-surface-card px-4 py-3">
          <li
            v-for="(item, index) in items"
            :key="index"
            class="flex items-start gap-2 text-body-sm text-content-secondary"
          >
            <span class="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-accent" />
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { InformationCircleIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  title: string
  items: string[]
}>()

const storageKey = `pageGuide_${props.title}`
const isOpen = ref(false)
const { t } = useI18n({ useScope: 'global' })

onMounted(() => {
  const saved = localStorage.getItem(storageKey)
  if (saved !== null) {
    isOpen.value = saved === 'true'
  }
})

function toggle() {
  isOpen.value = !isOpen.value
  localStorage.setItem(storageKey, String(isOpen.value))
}
</script>
