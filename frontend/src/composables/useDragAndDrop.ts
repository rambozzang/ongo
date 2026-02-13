import { ref, type Ref } from 'vue'

export interface DragHandlers {
  handleDragStart: (event: DragEvent, index: number) => void
  handleDragOver: (event: DragEvent, index: number) => void
  handleDragEnd: (event: DragEvent) => void
  handleDrop: (event: DragEvent, index: number) => void
}

export function useDragAndDrop<T>(
  items: Ref<T[]>,
  onReorder: (newItems: T[]) => void
) {
  const isDragging = ref(false)
  const dragIndex = ref<number | null>(null)
  const dropIndex = ref<number | null>(null)

  function handleDragStart(event: DragEvent, index: number) {
    isDragging.value = true
    dragIndex.value = index

    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData('text/plain', index.toString())
    }
  }

  function handleDragOver(event: DragEvent, index: number) {
    event.preventDefault()

    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move'
    }

    // Update drop index only if it's different
    if (dragIndex.value !== null && dragIndex.value !== index) {
      dropIndex.value = index
    }
  }

  function handleDragEnd(event: DragEvent) {
    event.preventDefault()
    isDragging.value = false
    dragIndex.value = null
    dropIndex.value = null
  }

  function handleDrop(event: DragEvent, index: number) {
    event.preventDefault()

    if (dragIndex.value === null || dragIndex.value === index) {
      handleDragEnd(event)
      return
    }

    // Create a new array with reordered items
    const newItems = [...items.value]
    const [draggedItem] = newItems.splice(dragIndex.value, 1)
    newItems.splice(index, 0, draggedItem)

    // Call the reorder callback
    onReorder(newItems)

    handleDragEnd(event)
  }

  const dragHandlers: DragHandlers = {
    handleDragStart,
    handleDragOver,
    handleDragEnd,
    handleDrop,
  }

  return {
    dragHandlers,
    isDragging,
    dragIndex,
    dropIndex,
  }
}
