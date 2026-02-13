import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface WidgetConfig {
  id: string
  label: string
  icon: string // heroicon component name
  visible: boolean
  order: number
}

const DEFAULT_WIDGETS: WidgetConfig[] = [
  { id: 'summary', label: '요약 카드', icon: 'ChartBarIcon', visible: true, order: 0 },
  { id: 'trend', label: '조회수 트렌드', icon: 'ChartLineIcon', visible: true, order: 1 },
  { id: 'platform', label: '플랫폼 비교', icon: 'ChartPieIcon', visible: true, order: 2 },
  { id: 'videos', label: '최근 업로드', icon: 'FilmIcon', visible: true, order: 3 },
  { id: 'schedules', label: '예약 업로드', icon: 'CalendarDaysIcon', visible: true, order: 4 },
  { id: 'miniWidgets', label: '미니 위젯', icon: 'Squares2X2Icon', visible: true, order: 5 },
]

const STORAGE_KEY = 'ongo-widget-settings'

export const useWidgetSettingsStore = defineStore('widgetSettings', () => {
  const widgets = ref<WidgetConfig[]>([])

  // Load from localStorage or use defaults
  function loadWidgets() {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      try {
        widgets.value = JSON.parse(stored)
      } catch {
        widgets.value = [...DEFAULT_WIDGETS]
      }
    } else {
      widgets.value = [...DEFAULT_WIDGETS]
    }
  }

  // Save to localStorage
  function saveWidgets() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(widgets.value))
  }

  // Toggle widget visibility
  function toggleWidget(id: string) {
    const widget = widgets.value.find((w) => w.id === id)
    if (widget) {
      widget.visible = !widget.visible
      saveWidgets()
    }
  }

  // Reorder widgets
  function reorderWidgets(newOrder: string[]) {
    const reordered = newOrder.map((id, index) => {
      const widget = widgets.value.find((w) => w.id === id)
      return widget ? { ...widget, order: index } : null
    }).filter((w): w is WidgetConfig => w !== null)

    widgets.value = reordered
    saveWidgets()
  }

  // Reset to default layout
  function resetToDefault() {
    widgets.value = [...DEFAULT_WIDGETS]
    saveWidgets()
  }

  // Get visible widgets sorted by order
  function getVisibleWidgets(): WidgetConfig[] {
    return widgets.value
      .filter((w) => w.visible)
      .sort((a, b) => a.order - b.order)
  }

  // Initialize on store creation
  loadWidgets()

  return {
    widgets,
    toggleWidget,
    reorderWidgets,
    resetToDefault,
    getVisibleWidgets,
  }
})
