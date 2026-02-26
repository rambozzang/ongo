<script setup lang="ts">
import { computed } from 'vue'
import MoodBoardItemCard from './MoodBoardItemCard.vue'
import type { MoodBoardItem } from '@/types/moodBoard'

interface Props {
  items: MoodBoardItem[]
}

const props = defineProps<Props>()

// Split items into columns for masonry-style layout
const columns = computed(() => {
  const cols: MoodBoardItem[][] = [[], [], []]
  const sortedItems = [...props.items].sort((a, b) => a.position - b.position)
  sortedItems.forEach((item, index) => {
    cols[index % 3].push(item)
  })
  return cols
})
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    <div
      v-for="(column, colIndex) in columns"
      :key="colIndex"
      class="flex flex-col gap-4"
    >
      <MoodBoardItemCard
        v-for="item in column"
        :key="item.id"
        :item="item"
      />
    </div>
  </div>
</template>
