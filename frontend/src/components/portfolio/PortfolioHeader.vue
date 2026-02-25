<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PencilIcon, CameraIcon } from '@heroicons/vue/24/outline'
import type { PortfolioProfile } from '@/types/portfolio'

const props = defineProps<{
  profile: PortfolioProfile
}>()

const emit = defineEmits<{
  (e: 'update', updates: Partial<PortfolioProfile>): void
}>()

const { t } = useI18n()
const isEditing = ref(false)
const editForm = ref({ ...props.profile })
const categoryInput = ref('')

function startEdit() {
  editForm.value = { ...props.profile }
  categoryInput.value = props.profile.categories.join(', ')
  isEditing.value = true
}

function saveEdit() {
  const categories = categoryInput.value
    .split(',')
    .map(c => c.trim())
    .filter(Boolean)
  emit('update', {
    displayName: editForm.value.displayName,
    bio: editForm.value.bio,
    categories,
    contactEmail: editForm.value.contactEmail,
    website: editForm.value.website,
  })
  isEditing.value = false
}

function cancelEdit() {
  isEditing.value = false
}
</script>

<template>
  <div class="card p-6">
    <div class="flex items-start justify-between">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.profile') }}
      </h2>
      <button
        v-if="!isEditing"
        class="text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
        @click="startEdit"
      >
        <PencilIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- View Mode -->
    <div v-if="!isEditing" class="mt-4">
      <div class="flex items-center gap-4">
        <div class="relative h-20 w-20 shrink-0">
          <div
            class="flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-primary-400 to-primary-600 text-2xl font-bold text-white"
          >
            {{ profile.displayName.charAt(0) }}
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <h3 class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ profile.displayName }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ profile.bio }}
          </p>
        </div>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <span
          v-for="cat in profile.categories"
          :key="cat"
          class="rounded-full bg-primary-50 px-3 py-1 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
        >
          {{ cat }}
        </span>
      </div>
      <div v-if="profile.contactEmail || profile.website" class="mt-3 space-y-1 text-sm text-gray-500 dark:text-gray-400">
        <div v-if="profile.contactEmail">{{ profile.contactEmail }}</div>
        <div v-if="profile.website">{{ profile.website }}</div>
      </div>
    </div>

    <!-- Edit Mode -->
    <div v-else class="mt-4 space-y-4">
      <div class="flex items-center gap-4">
        <div class="relative h-20 w-20 shrink-0">
          <div
            class="flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-primary-400 to-primary-600 text-2xl font-bold text-white"
          >
            {{ editForm.displayName.charAt(0) || '?' }}
          </div>
          <button
            class="absolute -bottom-1 -right-1 rounded-full bg-white p-1.5 shadow-md dark:bg-gray-700"
          >
            <CameraIcon class="h-4 w-4 text-gray-500 dark:text-gray-400" />
          </button>
        </div>
        <div class="flex-1">
          <input
            v-model="editForm.displayName"
            type="text"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            :placeholder="t('portfolio.namePlaceholder')"
          />
        </div>
      </div>
      <div>
        <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('portfolio.bio') }}</label>
        <textarea
          v-model="editForm.bio"
          rows="3"
          class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          :placeholder="t('portfolio.bioPlaceholder')"
        />
      </div>
      <div>
        <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('portfolio.categories') }}</label>
        <input
          v-model="categoryInput"
          type="text"
          class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          :placeholder="t('portfolio.categoriesPlaceholder')"
        />
      </div>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('portfolio.email') }}</label>
          <input
            v-model="editForm.contactEmail"
            type="email"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('portfolio.website') }}</label>
          <input
            v-model="editForm.website"
            type="url"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </div>
      </div>
      <div class="flex justify-end gap-3">
        <button class="btn-secondary" @click="cancelEdit">{{ t('portfolio.cancel') }}</button>
        <button class="btn-primary" @click="saveEdit">{{ t('portfolio.save') }}</button>
      </div>
    </div>
  </div>
</template>
