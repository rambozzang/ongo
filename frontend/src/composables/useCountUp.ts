import { ref, watch, type Ref } from 'vue'

export function useCountUp(target: Ref<number>, duration = 800) {
  const display = ref(0)

  watch(target, (newVal) => {
    const start = display.value
    const diff = newVal - start
    if (diff === 0) return

    const startTime = performance.now()

    function animate(currentTime: number) {
      const elapsed = currentTime - startTime
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic

      display.value = Math.round(start + diff * eased)

      if (progress < 1) {
        requestAnimationFrame(animate)
      }
    }

    requestAnimationFrame(animate)
  }, { immediate: true })

  return display
}
