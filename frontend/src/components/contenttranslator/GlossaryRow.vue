<script setup lang="ts">
import type { TranslationGlossary } from '@/types/contentTranslator'

defineProps<{
  glossary: TranslationGlossary
}>()

const languagePair = (source: string, target: string) => `${source} → ${target}`
</script>

<template>
  <tr class="border-b border-gray-100 transition-colors hover:bg-gray-50 dark:border-gray-800 dark:hover:bg-gray-800/50">
    <td class="px-4 py-3 text-sm font-medium text-gray-900 dark:text-gray-100">
      {{ glossary.sourceWord }}
    </td>
    <td class="px-4 py-3">
      <span class="text-gray-400 dark:text-gray-500 mr-2">→</span>
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ glossary.targetWord }}</span>
    </td>
    <td class="px-4 py-3">
      <span class="inline-flex items-center rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        {{ languagePair(glossary.sourceLanguage, glossary.targetLanguage) }}
      </span>
    </td>
    <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
      {{ glossary.context ?? '-' }}
    </td>
  </tr>
</template>
