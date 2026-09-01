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
      <!-- 이미 img 는 조건부였다. 없을 때 자리가 비어 있던 것을 편집·전화 미리보기와 같은 로컬 아이콘으로 맞춘다. -->
      <img
        v-if="page.avatarUrl"
        :src="page.avatarUrl"
        :alt="page.title || page.slug"
        class="mx-auto mb-4 h-20 w-20 rounded-full object-cover shadow-sm"
      />
      <span
        v-else
        role="img"
        :aria-label="page.title || page.slug"
        class="mx-auto mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-gray-200 shadow-sm dark:bg-gray-700"
      >
        <UserCircleIcon class="h-16 w-16 text-gray-400 dark:text-gray-500" />
      </span>
      <h1 class="text-2xl font-bold">{{ page.title || page.slug }}</h1>
      <p v-if="page.bio" class="mt-2 whitespace-pre-line text-sm opacity-80">{{ page.bio }}</p>

      <!--
        **서버 데이터라 방어적으로 본다.** 백엔드는 URL 을 검증하지 않고, 이번 수정 전에
        저장된 행에는 `https://` 같은 값이 남아 있을 수 있다. 그런 링크를 그리면 방문자를
        아무 데도 아닌 곳으로 보내고, 클릭 집계에도 실제 방문이 아닌 수치가 쌓인다.
        주소가 유효한 링크만 보여준다.
      -->
      <div class="mt-8 space-y-3">
        <a
          v-for="link in validLinks"
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
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { UserCircleIcon } from '@heroicons/vue/24/outline'
import { linkBioApi, type LinkBioPublicResponse } from '@/api/linkbio'
import { isValidLinkUrl } from '@/types/linkbio'

const route = useRoute()
const page = ref<LinkBioPublicResponse | null>(null)
const loading = ref(true)
const error = ref(false)
const slug = String(route.params.slug)

/**
 * 실제로 걸 수 있는 링크만. 검증 없이 저장된 옛 행을 그대로 그리지 않는다.
 *
 * 클릭 집계는 이 목록에서만 일어나므로, 유효한 링크의 기존 집계 동작은 그대로다.
 */
const validLinks = computed(() => (page.value?.links ?? []).filter(link => isValidLinkUrl(link.url)))

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
