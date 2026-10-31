<script setup lang="ts">
/**
 * "다음에 할 일" — 서버가 실측 성과에서만 뽑은 추천. 근거가 없거나 불러오지 못하면 카드를 숨긴다
 * (지어낸 조언으로 자리를 채우지 않는다).
 */
import { onMounted, ref } from 'vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import { analyticsApi, type NextAction } from '@/api/analytics'

const actions = ref<NextAction[]>([])

onMounted(async () => {
  try {
    actions.value = await analyticsApi.nextActions()
  } catch {
    actions.value = []
  }
})
</script>

<template>
  <SectionCard v-if="actions.length" :title="$t('redesign.today.nextActions.title')" data-testid="next-actions">
    <ul class="divide-y divide-line">
      <li v-for="a in actions" :key="`${a.type}-${a.videoId}`" class="flex flex-wrap items-center gap-3 py-2.5">
        <div class="min-w-0 flex-1">
          <p class="truncate text-[12.5px] font-semibold text-content">{{ $t('redesign.today.nextActions.makeShorts', { title: a.title }) }}</p>
          <p class="text-[11px] text-content-tertiary">{{ a.reason }}</p>
        </div>
        <router-link
          :to="{ path: '/ugc/shorts/runs', query: { videoId: String(a.videoId) } }"
          class="btn-primary inline-flex min-h-[44px] items-center !text-[11px]"
        >
          {{ $t('redesign.today.nextActions.makeShortsCta') }}
        </router-link>
      </li>
    </ul>
  </SectionCard>
</template>
