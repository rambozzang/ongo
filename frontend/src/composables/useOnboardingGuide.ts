import { ref, computed } from 'vue'

interface GuideStep {
  id: string
  label: string
  completed: boolean
}

export function useOnboardingGuide() {
  const STORAGE_KEY = 'ongo-guide-progress'

  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')

  const steps = ref<GuideStep[]>([
    { id: 'upload', label: '첫 영상 업로드', completed: saved.upload ?? false },
    { id: 'ai', label: 'AI로 제목 최적화', completed: saved.ai ?? false },
    { id: 'publish', label: '첫 게시 완료', completed: saved.publish ?? false },
    { id: 'analytics', label: '분석 확인', completed: saved.analytics ?? false },
  ])

  const dismissed = ref(saved._dismissed ?? false)

  const completedCount = computed(() => steps.value.filter(s => s.completed).length)
  const allCompleted = computed(() => completedCount.value === steps.value.length)
  const showGuide = computed(() => !dismissed.value && !allCompleted.value)
  const progress = computed(() => Math.round((completedCount.value / steps.value.length) * 100))

  function completeStep(id: string) {
    const step = steps.value.find(s => s.id === id)
    if (step) {
      step.completed = true
      save()
    }
  }

  function dismiss() {
    dismissed.value = true
    save()
  }

  function save() {
    const data: Record<string, boolean> = { _dismissed: dismissed.value }
    steps.value.forEach(s => { data[s.id] = s.completed })
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  }

  return { steps, showGuide, completedCount, allCompleted, progress, completeStep, dismiss }
}
