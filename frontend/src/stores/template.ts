import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MetadataTemplate } from '@/types/template'
import type { MetadataFormData } from '@/components/upload/MetadataForm.vue'

const STORAGE_KEY = 'ongo-templates'
const MAX_TEMPLATES = 20

const DEFAULT_TEMPLATES: MetadataTemplate[] = [
  {
    id: 'default-vlog',
    name: 'YouTube Vlog',
    description: '일상 브이로그용 템플릿',
    titlePattern: '[Vlog] {title} | 일상 브이로그',
    descriptionTemplate: `안녕하세요! 오늘의 브이로그를 공유합니다.

영상이 마음에 드셨다면 좋아요와 구독 부탁드립니다!

📌 타임라인
00:00 인트로
00:30 본편
04:00 마무리

💬 댓글로 여러분의 의견을 남겨주세요!`,
    tags: ['브이로그', 'vlog', '일상', '데일리', 'daily'],
    category: '인물/블로그',
    visibility: 'PUBLIC',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'default-shorts',
    name: '숏폼 콘텐츠',
    description: '틱톡, 릴스, 쇼츠용 템플릿',
    titlePattern: '{title} #숏츠',
    descriptionTemplate: `🔥 {title}

좋아요와 팔로우 부탁드립니다!

#shorts #릴스 #틱톡`,
    tags: ['숏츠', 'shorts', '릴스', 'reels', '틱톡', 'tiktok'],
    category: '엔터테인먼트',
    visibility: 'PUBLIC',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'default-educational',
    name: '교육 콘텐츠',
    description: '강의, 튜토리얼용 템플릿',
    titlePattern: '[강의] {title} - 완벽 가이드',
    descriptionTemplate: `이번 영상에서는 {title}에 대해 알아봅니다.

📚 목차
1. 개요
2. 핵심 내용
3. 실습
4. 정리

🔔 구독과 알림 설정으로 다음 강의를 놓치지 마세요!

📧 문의: contact@example.com`,
    tags: ['강의', '튜토리얼', 'tutorial', '교육', '강좌'],
    category: '교육',
    visibility: 'PUBLIC',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
]

export const useTemplateStore = defineStore('template', () => {
  const templates = ref<MetadataTemplate[]>([])
  const initialized = ref(false)

  const sortedTemplates = computed(() => {
    return [...templates.value].sort((a, b) => {
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    })
  })

  function loadTemplates() {
    if (initialized.value) return

    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        templates.value = Array.isArray(parsed) ? parsed : []
      } else {
        templates.value = [...DEFAULT_TEMPLATES]
        saveToStorage()
      }
    } catch {
      templates.value = [...DEFAULT_TEMPLATES]
      saveToStorage()
    }

    initialized.value = true
  }

  function saveToStorage() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(templates.value))
    } catch (error) {
      console.error('Failed to save templates to localStorage:', error)
    }
  }

  function createTemplate(data: Omit<MetadataTemplate, 'id' | 'createdAt' | 'updatedAt'>): MetadataTemplate | null {
    if (templates.value.length >= MAX_TEMPLATES) {
      return null
    }

    const newTemplate: MetadataTemplate = {
      ...data,
      id: `template-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }

    templates.value.push(newTemplate)
    saveToStorage()
    return newTemplate
  }

  function updateTemplate(id: string, data: Partial<Omit<MetadataTemplate, 'id' | 'createdAt'>>): boolean {
    const index = templates.value.findIndex((t: MetadataTemplate) => t.id === id)
    if (index === -1) return false

    templates.value[index] = {
      ...templates.value[index],
      ...data,
      updatedAt: new Date().toISOString(),
    }

    saveToStorage()
    return true
  }

  function deleteTemplate(id: string): boolean {
    const index = templates.value.findIndex((t: MetadataTemplate) => t.id === id)
    if (index === -1) return false

    templates.value.splice(index, 1)
    saveToStorage()
    return true
  }

  function duplicateTemplate(id: string): MetadataTemplate | null {
    if (templates.value.length >= MAX_TEMPLATES) {
      return null
    }

    const original = templates.value.find((t: MetadataTemplate) => t.id === id)
    if (!original) return null

    const duplicated: MetadataTemplate = {
      ...original,
      id: `template-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      name: `${original.name} (복사본)`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }

    templates.value.push(duplicated)
    saveToStorage()
    return duplicated
  }

  function applyTemplate(templateId: string, currentTitle?: string): MetadataFormData | null {
    const template = templates.value.find((t: MetadataTemplate) => t.id === templateId)
    if (!template) return null

    // Replace {title} placeholder in titlePattern
    let finalTitle = template.titlePattern
    if (currentTitle) {
      finalTitle = template.titlePattern.replace('{title}', currentTitle)
    } else {
      // Remove placeholder if no current title
      finalTitle = template.titlePattern.replace('{title}', '').trim()
    }

    // Replace {title} placeholder in descriptionTemplate
    let finalDescription = template.descriptionTemplate
    if (currentTitle) {
      finalDescription = template.descriptionTemplate.replace(/{title}/g, currentTitle)
    }

    // Update template's updatedAt to track last usage
    updateTemplate(templateId, {})

    return {
      title: finalTitle,
      description: finalDescription,
      tags: [...template.tags],
      category: template.category,
      visibility: template.visibility as MetadataFormData['visibility'],
      thumbnailUrl: '',
    }
  }

  function getTemplate(id: string): MetadataTemplate | undefined {
    return templates.value.find((t: MetadataTemplate) => t.id === id)
  }

  return {
    templates: sortedTemplates,
    loadTemplates,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    duplicateTemplate,
    applyTemplate,
    getTemplate,
  }
})
