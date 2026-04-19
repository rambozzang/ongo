import { defineStore } from 'pinia'
import { ref } from 'vue'
import { tagTemplateApi } from '@/api/tagTemplate'
import type { TagTemplate, TagTemplateCreateRequest, TagTemplateUpdateRequest } from '@/types/tagTemplate'
import { useNotificationStore } from '@/stores/notification'

export const useTagTemplateStore = defineStore('tagTemplate', () => {
  const templates = ref<TagTemplate[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      templates.value = await tagTemplateApi.list()
    } catch (e) {
      useNotificationStore().error(e instanceof Error ? e.message : '태그 템플릿을 불러오지 못했습니다')
    } finally {
      loading.value = false
    }
  }

  async function create(request: TagTemplateCreateRequest) {
    const created = await tagTemplateApi.create(request)
    templates.value.unshift(created)
    return created
  }

  async function update(id: number, request: TagTemplateUpdateRequest) {
    const updated = await tagTemplateApi.update(id, request)
    const idx = templates.value.findIndex((t) => t.id === id)
    if (idx !== -1) templates.value[idx] = updated
    return updated
  }

  async function remove(id: number) {
    await tagTemplateApi.delete(id)
    templates.value = templates.value.filter((t) => t.id !== id)
  }

  return { templates, loading, fetchAll, create, update, remove }
})
