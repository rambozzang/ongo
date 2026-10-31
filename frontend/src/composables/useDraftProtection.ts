import { computed, onBeforeUnmount, onMounted, ref, type ComputedRef } from 'vue'

/** Keep the acknowledged snapshot, including edits made while a save is in flight. */
export function useDraftProtection(
  snapshot: ComputedRef<string>,
  hasContent: ComputedRef<boolean>,
) {
  const savedSnapshot = ref(snapshot.value)
  const savedHasContent = ref(hasContent.value)
  const leaveRequested = ref(false)
  const dirty = computed(
    () => (hasContent.value || savedHasContent.value) && snapshot.value !== savedSnapshot.value,
  )
  let resolveLeave: ((leave: boolean) => void) | null = null

  function markSaved(acknowledgedSnapshot = snapshot.value) {
    savedSnapshot.value = acknowledgedSnapshot
    savedHasContent.value = true
  }

  function requestLeave(): boolean | Promise<boolean> {
    if (!dirty.value) return true
    // A new navigation supersedes a pending one. Never leave an old router
    // navigation waiting indefinitely.
    resolveLeave?.(false)
    leaveRequested.value = true
    return new Promise((resolve) => {
      resolveLeave = resolve
    })
  }

  function settleLeave(leave: boolean) {
    leaveRequested.value = false
    const resolve = resolveLeave
    resolveLeave = null
    resolve?.(leave)
  }

  function beforeUnload(event: BeforeUnloadEvent) {
    if (!dirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    settleLeave(false)
  })

  return { dirty, leaveRequested, markSaved, requestLeave, settleLeave }
}
