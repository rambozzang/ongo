import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ContentTemplate, TemplateCategory, TemplatePlatform } from '@/types/template'
import { templatesApi } from '@/api/templates'

const STORAGE_KEY = 'ongo_content_templates'

export const useTemplatesStore = defineStore('templates', () => {
  const templates = ref<ContentTemplate[]>([])
  const searchText = ref('')
  const categoryFilter = ref<TemplateCategory | 'all'>('all')
  const platformFilter = ref<TemplatePlatform | 'all'>('all')
  const sortBy = ref<'latest' | 'usage' | 'name'>('latest')
  const showFavoritesOnly = ref(false)

  // Load templates - try API first, fallback to localStorage/mock
  const loadTemplates = async () => {
    try {
      const response = await templatesApi.list({ page: 0, size: 100 })
      templates.value = response.templates.map((t) => ({
        id: t.id,
        name: t.name,
        category: (t.category ?? 'full') as TemplateCategory,
        platform: (t.platform ?? 'ALL') as TemplatePlatform,
        titleTemplate: t.titleTemplate ?? undefined,
        descriptionTemplate: t.descriptionTemplate ?? undefined,
        tagsTemplate: t.tags.length > 0 ? t.tags : undefined,
        variables: [],
        usageCount: t.usageCount,
        isFavorite: false,
        createdAt: t.createdAt ?? new Date().toISOString(),
        updatedAt: t.updatedAt ?? new Date().toISOString(),
      }))
    } catch {
      // Fallback to localStorage
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        try {
          templates.value = JSON.parse(stored)
        } catch {
          templates.value = []
        }
      } else {
        templates.value = []
      }
    }
  }

  // Save to localStorage
  const saveTemplates = () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(templates.value))
  }

  // Getters
  const templatesByCategory = computed(() => {
    const grouped: Record<TemplateCategory | 'all', ContentTemplate[]> = {
      all: templates.value,
      title: templates.value.filter((t) => t.category === 'title'),
      description: templates.value.filter((t) => t.category === 'description'),
      tags: templates.value.filter((t) => t.category === 'tags'),
      thumbnail: templates.value.filter((t) => t.category === 'thumbnail'),
      full: templates.value.filter((t) => t.category === 'full'),
    }
    return grouped
  })

  const favoriteTemplates = computed(() => {
    return templates.value.filter((t) => t.isFavorite)
  })

  const filteredTemplates = computed(() => {
    let result = templates.value

    if (categoryFilter.value !== 'all') {
      result = result.filter((t) => t.category === categoryFilter.value)
    }

    if (platformFilter.value !== 'all') {
      result = result.filter((t) => t.platform === platformFilter.value || t.platform === 'ALL')
    }

    if (showFavoritesOnly.value) {
      result = result.filter((t) => t.isFavorite)
    }

    if (searchText.value.trim()) {
      const search = searchText.value.toLowerCase()
      result = result.filter(
        (t) =>
          t.name.toLowerCase().includes(search) ||
          t.titleTemplate?.toLowerCase().includes(search) ||
          t.descriptionTemplate?.toLowerCase().includes(search) ||
          t.tagsTemplate?.some((tag) => tag.toLowerCase().includes(search)),
      )
    }

    if (sortBy.value === 'latest') {
      result.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    } else if (sortBy.value === 'usage') {
      result.sort((a, b) => b.usageCount - a.usageCount)
    } else if (sortBy.value === 'name') {
      result.sort((a, b) => a.name.localeCompare(b.name, 'ko'))
    }

    return result
  })

  // Actions
  const createTemplate = async (template: Omit<ContentTemplate, 'id' | 'usageCount' | 'createdAt' | 'updatedAt'>) => {
    try {
      const response = await templatesApi.create({
        name: template.name,
        titleTemplate: template.titleTemplate,
        descriptionTemplate: template.descriptionTemplate,
        tags: template.tagsTemplate ?? [],
        category: template.category,
        platform: template.platform === 'ALL' ? undefined : template.platform,
      })
      const newTemplate: ContentTemplate = {
        id: response.id,
        name: response.name,
        category: (response.category ?? template.category) as TemplateCategory,
        platform: (response.platform ?? 'ALL') as TemplatePlatform,
        titleTemplate: response.titleTemplate ?? undefined,
        descriptionTemplate: response.descriptionTemplate ?? undefined,
        tagsTemplate: response.tags.length > 0 ? response.tags : undefined,
        variables: template.variables,
        usageCount: 0,
        isFavorite: false,
        createdAt: response.createdAt ?? new Date().toISOString(),
        updatedAt: response.updatedAt ?? new Date().toISOString(),
      }
      templates.value.push(newTemplate)
      return newTemplate
    } catch {
      // Fallback to local creation
      const newTemplate: ContentTemplate = {
        ...template,
        id: Math.max(0, ...templates.value.map((t) => t.id)) + 1,
        usageCount: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      templates.value.push(newTemplate)
      saveTemplates()
      return newTemplate
    }
  }

  const updateTemplate = async (id: number, updates: Partial<ContentTemplate>) => {
    const index = templates.value.findIndex((t) => t.id === id)
    if (index === -1) return

    try {
      const current = templates.value[index]
      await templatesApi.update(id, {
        name: updates.name ?? current.name,
        titleTemplate: updates.titleTemplate ?? current.titleTemplate,
        descriptionTemplate: updates.descriptionTemplate ?? current.descriptionTemplate,
        tags: updates.tagsTemplate ?? current.tagsTemplate ?? [],
        category: updates.category ?? current.category,
        platform: updates.platform ?? current.platform,
      })
    } catch {
      // Continue with local update
    }

    templates.value[index] = {
      ...templates.value[index],
      ...updates,
      updatedAt: new Date().toISOString(),
    }
    saveTemplates()
  }

  const deleteTemplate = async (id: number) => {
    try {
      await templatesApi.delete(id)
    } catch {
      // Continue with local delete
    }
    const index = templates.value.findIndex((t) => t.id === id)
    if (index !== -1) {
      templates.value.splice(index, 1)
      saveTemplates()
    }
  }

  const duplicateTemplate = (id: number) => {
    const original = templates.value.find((t) => t.id === id)
    if (original) {
      const duplicate = {
        ...original,
        name: `${original.name} (복사본)`,
        isFavorite: false,
      }
      return createTemplate(duplicate)
    }
  }

  const toggleFavorite = (id: number) => {
    const template = templates.value.find((t) => t.id === id)
    if (template) {
      template.isFavorite = !template.isFavorite
      template.updatedAt = new Date().toISOString()
      saveTemplates()
    }
  }

  const applyTemplate = async (id: number) => {
    try {
      await templatesApi.use(id)
    } catch {
      // Continue locally
    }
    const template = templates.value.find((t) => t.id === id)
    if (template) {
      template.usageCount++
      template.updatedAt = new Date().toISOString()
      saveTemplates()
      return template
    }
  }

  // Initialize
  loadTemplates()

  return {
    templates,
    searchText,
    categoryFilter,
    platformFilter,
    sortBy,
    showFavoritesOnly,
    templatesByCategory,
    favoriteTemplates,
    filteredTemplates,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    duplicateTemplate,
    toggleFavorite,
    applyTemplate,
  }
})
