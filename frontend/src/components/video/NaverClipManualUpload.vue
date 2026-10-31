<script setup lang="ts">
/**
 * 네이버 클립 **직접 올리기** 도우미.
 *
 * 네이버는 클립 업로드 공개 API 를 제공하지 않는다. 그래서 자동 게시처럼 보이게 하지 않고,
 * 사람이 PC 웹(클립 크리에이터)에서 올리는 데 필요한 것만 준비한다:
 * 영상 파일 내려받기 · 제목/설명·해시태그 복사 · 업로드 페이지 열기.
 * 올린 뒤의 성과는 onGo 가 수집하지 못한다는 점도 함께 말한다.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  title: string
  description: string | null
  tags: string[]
  fileUrl: string | null
}>()

const { t } = useI18n()

/** 클립 크리에이터 PC 웹. 3분 넘는 영상·예약·여러 개 올리기는 PC 웹에서만 된다. */
const UPLOAD_URL = 'https://clipcreators.naver.com'
const TITLE_MAX = 100

const title = computed(() => props.title.trim().slice(0, TITLE_MAX))
const body = computed(() => {
  const tags = props.tags
    .map((tag) => tag.trim().replace(/^#/, '').replace(/\s+/g, ''))
    .filter((tag) => tag.length > 0)
  const hashtags = [...new Set(tags)].slice(0, 5).map((tag) => `#${tag}`).join(' ')
  return [props.description?.trim() ?? '', hashtags].filter((part) => part.length > 0).join('\n\n')
})

const copied = ref<'title' | 'body' | null>(null)
const copyError = ref(false)

async function copy(kind: 'title' | 'body') {
  copyError.value = false
  try {
    await navigator.clipboard.writeText(kind === 'title' ? title.value : body.value)
    copied.value = kind
  } catch {
    copyError.value = true
  }
}
</script>

<template>
  <div class="card" data-testid="naver-clip-manual">
    <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ t('naverClipManual.title') }}</h3>
    <p class="mt-1 text-body-xs text-gray-600 dark:text-gray-300">{{ t('naverClipManual.notice') }}</p>

    <ol class="mt-3 space-y-3 text-body text-gray-800 dark:text-gray-200">
      <li>
        <p class="font-medium">{{ t('naverClipManual.step1') }}</p>
        <a
          v-if="fileUrl"
          :href="fileUrl"
          download
          target="_blank"
          rel="noopener"
          class="btn-secondary mt-1 inline-flex min-h-[44px] items-center"
          data-testid="naver-clip-download"
        >{{ t('naverClipManual.download') }}</a>
        <p v-else class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">{{ t('naverClipManual.noFile') }}</p>
      </li>
      <li>
        <p class="font-medium">{{ t('naverClipManual.step2') }}</p>
        <div class="mt-1 flex flex-wrap gap-2">
          <button type="button" class="btn-secondary min-h-[44px]" data-testid="naver-clip-copy-title" @click="copy('title')">
            {{ copied === 'title' ? t('naverClipManual.copied') : t('naverClipManual.copyTitle') }}
          </button>
          <button type="button" class="btn-secondary min-h-[44px]" data-testid="naver-clip-copy-body" :disabled="!body" @click="copy('body')">
            {{ copied === 'body' ? t('naverClipManual.copied') : t('naverClipManual.copyBody') }}
          </button>
        </div>
        <p v-if="copyError" class="mt-1 text-body-xs text-red-600 dark:text-red-400" role="alert">{{ t('naverClipManual.copyFailed') }}</p>
      </li>
      <li>
        <p class="font-medium">{{ t('naverClipManual.step3') }}</p>
        <a
          :href="UPLOAD_URL"
          target="_blank"
          rel="noopener"
          class="btn-primary mt-1 inline-flex min-h-[44px] items-center"
          data-testid="naver-clip-open"
        >{{ t('naverClipManual.open') }}</a>
      </li>
    </ol>
  </div>
</template>
