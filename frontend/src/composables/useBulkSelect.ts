import { ref, computed, type Ref } from 'vue'

export interface BulkSelectItem {
  id: number
  [key: string]: unknown
}

export function useBulkSelect<T extends BulkSelectItem>(items: Ref<T[]>) {
  const selectedIds = ref<Set<number>>(new Set())

  const isAllSelected = computed(() => {
    if (items.value.length === 0) return false
    return items.value.every((item) => selectedIds.value.has(item.id))
  })

  const isIndeterminate = computed(() => {
    if (items.value.length === 0) return false
    const selectedCount = items.value.filter((item) => selectedIds.value.has(item.id)).length
    return selectedCount > 0 && selectedCount < items.value.length
  })

  const selectedCount = computed(() => selectedIds.value.size)

  function toggleItem(id: number) {
    const next = new Set(selectedIds.value)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    selectedIds.value = next
  }

  function toggleAll() {
    if (isAllSelected.value) {
      selectedIds.value = new Set()
    } else {
      selectedIds.value = new Set(items.value.map((item) => item.id))
    }
  }

  function clearSelection() {
    selectedIds.value = new Set()
  }

  function isSelected(id: number) {
    return selectedIds.value.has(id)
  }

  return {
    selectedIds,
    isAllSelected,
    isIndeterminate,
    selectedCount,
    toggleItem,
    toggleAll,
    clearSelection,
    isSelected,
  }
}
