<template>
  <main
    class="flex min-h-screen items-start justify-center px-4 py-10"
    :style="{ backgroundColor: page?.backgroundColor ?? '#f8fafc', color: page?.textColor ?? '#111827' }"
  >
    <section v-if="loading" class="w-full max-w-md rounded-2xl bg-white/80 p-8 text-center shadow-sm">
      링크 페이지를 불러오는 중입니다…
    </section>

    <section v-else-if="error" class="w-full max-w-md rounded-2xl bg-white p-8 text-center shadow-sm">
      <h1 class="text-xl font-semibold">페이지를 찾을 수 없습니다</h1>
      <p class="mt-2 text-sm text-gray-500">링크가 삭제되었거나 아직 공개되지 않았습니다.</p>
    </section>

    <section v-else-if="page" class="w-full max-w-md text-center">
      <img
        v-if="page.avatarUrl"
        :src="page.avatarUrl"
        :alt="page.title || page.slug"
        class="mx-auto mb-4 h-20 w-20 rounded-full object-cover shadow-sm"
      />
      <h1 class="text-2xl font-bold">{{ page.title || page.slug }}</h1>
      <p v-if="page.bio" class="mt-2 whitespace-pre-line text-sm opacity-80">{{ page.bio }}</p>

      <div class="mt-8 space-y-3">
        <a
          v-for="link in page.links"
          :key="link.id"
          :href="link.url"
          target="_blank"
          rel="noopener noreferrer"
          class="block rounded-xl px-5 py-3 font-medium shadow-sm transition-opacity hover:opacity-85"
          :style="{ backgroundColor: page.buttonColor, color: page.buttonTextColor }"
          @click="recordClick(link.id)"
        >
          <span v-if="link.icon" class="mr-2">{{ link.icon }}</span>{{ link.title }}
        </a>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { linkBioApi, type LinkBioPublicResponse } from '@/api/linkbio'

const route = useRoute()
const page = ref<LinkBioPublicResponse | null>(null)
const loading = ref(true)
const error = ref(false)
const slug = String(route.params.slug)

async function recordClick(linkId: number) {
  await linkBioApi.recordClick(slug, linkId).catch(() => undefined)
}

onMounted(async () => {
  try {
    page.value = await linkBioApi.getPublicPage(slug)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>
