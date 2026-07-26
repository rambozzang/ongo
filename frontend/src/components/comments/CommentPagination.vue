<template>
  <!-- 프로젝트 표준 페이지네이션 패턴 -->
  <div v-if="totalPages > 1" class="flex items-center justify-between">
    <p class="text-sm text-gray-600 dark:text-gray-400">
      {{ page * pageSize + 1 }}–{{ Math.min((page + 1) * pageSize, totalCount) }} / {{ totalCount }}
    </p>
    <div class="flex gap-2">
      <button class="btn-secondary" :disabled="!hasPrevPage" @click="emit('prev')">
        {{ $t('commentsView.pagination.previous') }}
      </button>
      <button class="btn-secondary" :disabled="!hasNextPage" @click="emit('next')">
        {{ $t('commentsView.pagination.next') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
  hasPrevPage: boolean
  hasNextPage: boolean
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()
</script>
