<template>
  <span role="status" :aria-label="`상태: ${label}`" :class="badgeClass">
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { UploadStatus } from '@/types/video'

const props = defineProps<{
  status: UploadStatus
}>()

const statusMap: Record<UploadStatus, { label: string; class: string }> = {
  DRAFT: { label: '임시저장', class: 'badge-gray' },
  UPLOADING: { label: '업로드중', class: 'badge-blue' },
  PROCESSING: { label: '처리중', class: 'badge-warning' },
  REVIEW: { label: '검토중', class: 'badge-warning' },
  PUBLISHED: { label: '게시완료', class: 'badge-success' },
  FAILED: { label: '실패', class: 'badge-danger' },
  REJECTED: { label: '반려', class: 'badge-danger' },
}

const label = computed(() => statusMap[props.status].label)
const badgeClass = computed(() => statusMap[props.status].class)
</script>
