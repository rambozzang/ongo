<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('agency.clientLinks') }}
      </h3>
      <button class="btn-primary inline-flex items-center gap-1.5 text-sm" @click="showCreateModal = true">
        <PlusIcon class="h-4 w-4" />
        {{ $t('agency.createLink') }}
      </button>
    </div>

    <div v-if="links.length === 0" class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-800">
      <LinkIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
      <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">{{ $t('agency.noClientLinks') }}</p>
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="link in links"
        :key="link.id"
        class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ link.creatorName }}</p>
              <span
                class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
                :class="link.isActive
                  ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'"
              >
                {{ link.isActive ? $t('agency.linkActive') : $t('agency.linkExpired') }}
              </span>
            </div>
            <div class="mt-1 flex items-center gap-2">
              <code class="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400">
                /portal/{{ link.token }}
              </code>
              <button
                class="text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400"
                @click="copyLink(link.token)"
              >
                {{ copied === link.token ? $t('agency.copied') : $t('agency.copyLink') }}
              </button>
            </div>
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
              {{ $t('agency.expiresAt') }}: {{ formatDate(link.expiresAt) }}
            </p>
          </div>
          <button
            class="rounded-md p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
            @click="$emit('revoke', link.id)"
          >
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>

    <!-- Create Link Modal -->
    <Teleport to="body">
      <div
        v-if="showCreateModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="showCreateModal = false"
      >
        <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800">
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('agency.createPortalLink') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('agency.createLinkDescription') }}
          </p>
          <div class="mt-4 space-y-2">
            <button
              v-for="creator in creators"
              :key="creator.id"
              class="flex w-full items-center gap-3 rounded-lg border border-gray-200 p-3 text-left transition-colors hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700"
              @click="handleCreateLink(creator.id)"
            >
              <div
                class="flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold text-white"
                :style="{ backgroundColor: colors[creator.id % colors.length] }"
              >
                {{ creator.name.charAt(0) }}
              </div>
              <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ creator.name }}</span>
            </button>
          </div>
          <button
            class="btn-secondary mt-4 w-full"
            @click="showCreateModal = false"
          >
            {{ $t('agency.cancel') }}
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { PlusIcon, LinkIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { ClientPortalLink, AgencyCreator } from '@/types/agency'

defineProps<{
  links: ClientPortalLink[]
  creators: AgencyCreator[]
}>()

const emit = defineEmits<{
  revoke: [linkId: number]
  create: [creatorId: number]
}>()

const showCreateModal = ref(false)
const copied = ref<string | null>(null)
const colors = ['#6366F1', '#EC4899', '#10B981', '#F59E0B', '#3B82F6', '#8B5CF6']

function handleCreateLink(creatorId: number) {
  emit('create', creatorId)
  showCreateModal.value = false
}

function copyLink(token: string) {
  navigator.clipboard.writeText(`${window.location.origin}/portal/${token}`)
  copied.value = token
  setTimeout(() => { copied.value = null }, 2000)
}

function formatDate(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}
</script>
