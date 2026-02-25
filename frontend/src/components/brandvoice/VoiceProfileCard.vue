<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm transition-all hover:shadow-md">
    <!-- Header: Name + Status -->
    <div class="mb-3 flex items-start justify-between">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ profile.name }}
        </h3>
      </div>
      <span
        class="ml-2 shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
        :class="statusClasses"
      >
        {{ $t(`brandVoice.trainStatus.${profile.trainStatus}`) }}
      </span>
    </div>

    <!-- Tone Badge -->
    <div class="mb-3">
      <span
        class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
        :class="toneClasses"
      >
        {{ $t(`brandVoice.tones.${profile.tone}`) }}
      </span>
    </div>

    <!-- Stats -->
    <div class="mb-3 grid grid-cols-2 gap-2 text-xs text-gray-500 dark:text-gray-400">
      <div>
        <span>{{ $t('brandVoice.profiles.sampleCount', { count: profile.sampleCount }) }}</span>
      </div>
      <div>
        <span>{{ $t('brandVoice.profiles.emojiUsage') }}: {{ $t(`brandVoice.emojiLevels.${profile.emojiUsage}`) }}</span>
      </div>
    </div>

    <!-- Signature Phrase -->
    <div v-if="profile.signaturePhrase" class="mb-3">
      <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('brandVoice.profiles.signaturePhrase') }}</p>
      <p class="mt-0.5 text-sm italic text-gray-700 dark:text-gray-300">"{{ profile.signaturePhrase }}"</p>
    </div>

    <!-- Vocabulary Tags -->
    <div v-if="profile.vocabulary.length > 0" class="mb-3">
      <p class="mb-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('brandVoice.profiles.vocabulary') }}</p>
      <div class="flex flex-wrap gap-1">
        <span
          v-for="word in displayedVocabulary"
          :key="word"
          class="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs text-gray-600 dark:text-gray-400"
        >
          {{ word }}
        </span>
        <span
          v-if="remainingVocabCount > 0"
          class="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs text-gray-400 dark:text-gray-500"
        >
          {{ $t('brandVoice.profiles.moreVocab', { count: remainingVocabCount }) }}
        </span>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-2 border-t border-gray-100 dark:border-gray-800 pt-3">
      <button
        v-if="profile.trainStatus === 'READY'"
        class="btn-primary flex-1 text-xs"
        @click="$emit('select', profile)"
      >
        {{ $t('brandVoice.profiles.selectButton') }}
      </button>
      <button
        class="rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-1.5 text-xs text-gray-500 dark:text-gray-400 transition-colors hover:border-red-300 dark:hover:border-red-700 hover:text-red-600 dark:hover:text-red-400"
        @click="handleDelete"
      >
        {{ $t('brandVoice.profiles.deleteButton') }}
      </button>
    </div>

    <!-- Delete Confirmation -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
      >
        <div class="fixed inset-0 bg-black/50" @click="showDeleteConfirm = false" />
        <div class="relative w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h3 class="mb-2 text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('brandVoice.profiles.deleteConfirm') }}
          </h3>
          <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('brandVoice.profiles.deleteConfirmDescription') }}
          </p>
          <div class="flex justify-end gap-2">
            <button
              class="rounded-lg border border-gray-200 dark:border-gray-700 px-4 py-2 text-sm text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
              @click="showDeleteConfirm = false"
            >
              {{ $t('brandVoice.profiles.cancelButton') }}
            </button>
            <button
              class="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-red-700"
              @click="confirmDelete"
            >
              {{ $t('brandVoice.profiles.deleteConfirmButton') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { BrandVoiceProfile } from '@/types/brandVoice'

const props = defineProps<{
  profile: BrandVoiceProfile
}>()

const emit = defineEmits<{
  select: [profile: BrandVoiceProfile]
  delete: [id: number]
}>()

const showDeleteConfirm = ref(false)

const MAX_VOCAB_DISPLAY = 5

const displayedVocabulary = computed(() =>
  props.profile.vocabulary.slice(0, MAX_VOCAB_DISPLAY),
)

const remainingVocabCount = computed(() =>
  Math.max(0, props.profile.vocabulary.length - MAX_VOCAB_DISPLAY),
)

const toneClasses = computed(() => {
  const map: Record<string, string> = {
    CASUAL: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400',
    PROFESSIONAL: 'bg-slate-100 dark:bg-slate-900/30 text-slate-700 dark:text-slate-400',
    HUMOROUS: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400',
    EDUCATIONAL: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400',
    INSPIRATIONAL: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400',
  }
  return map[props.profile.tone] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-400'
})

const statusClasses = computed(() => {
  const map: Record<string, string> = {
    IDLE: 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
    TRAINING: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400',
    READY: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400',
    FAILED: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400',
  }
  return map[props.profile.trainStatus] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
})

function handleDelete() {
  showDeleteConfirm.value = true
}

function confirmDelete() {
  showDeleteConfirm.value = false
  emit('delete', props.profile.id)
}
</script>
