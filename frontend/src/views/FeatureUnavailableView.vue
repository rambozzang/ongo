<template>
  <div class="flex min-h-full items-center justify-center px-5 py-10">
    <section
      class="w-full max-w-lg rounded-2xl border border-line bg-surface-card p-6 text-center shadow-sm"
      role="alert"
    >
      <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-warning-subtle text-warning-strong">
        <ExclamationTriangleIcon class="h-6 w-6" aria-hidden="true" />
      </div>
      <h1 class="mt-4 text-lg font-bold text-content">{{ t('redesign.featureUnavailable.title') }}</h1>
      <p class="mx-auto mt-2 max-w-md text-[12px] leading-5 text-content-secondary">
        {{ t('redesign.featureUnavailable.description') }}
      </p>
      <p
        v-if="unavailableReason"
        class="mx-auto mt-3 max-w-md rounded-lg border border-warning bg-warning-subtle px-3 py-2 text-left text-[11px] leading-5 text-warning-strong"
      >
        <span class="font-semibold">{{ t('redesign.featureUnavailable.reasonLabel') }}</span>
        {{ unavailableReason }}
      </p>
      <div class="mt-5 flex flex-wrap justify-center gap-2">
        <button type="button" class="btn-secondary !min-h-9 !px-3 text-[11px]" @click="retry">
          {{ t('action.retry') }}
        </button>
        <button type="button" class="btn-primary !min-h-9 !px-3 text-[11px]" @click="goToday">
          {{ t('redesign.featureUnavailable.backToToday') }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import { capabilitiesApi } from '@/api/capabilities'
import { requiredCapabilityForPath } from '@/router/capability'

const router = useRouter()
const route = useRoute()
const { t } = useLocale()
const unavailableReason = ref<string | null>(null)

onMounted(async () => {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  const capabilityKey = requiredCapabilityForPath(from)
  if (!capabilityKey) return

  const capabilities = await capabilitiesApi.list().catch(() => [])
  unavailableReason.value = capabilities.find((capability) => capability.key === capabilityKey)?.reason ?? null
})

async function retry() {
  capabilitiesApi.clearCache()
  const from = typeof route.query.from === 'string'
    && route.query.from.startsWith('/')
    && !route.query.from.startsWith('//')
    ? route.query.from
    : '/today'
  await router.replace(from)
}

function goToday() {
  void router.push('/today')
}
</script>
