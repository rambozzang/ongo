import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  ugcShortsPromptApi,
  type PipelineStage,
  type ShortsPromptResponse,
  type ShortsPromptRevisionResponse,
  type UpdateShortsPromptRequest,
} from '@/api/ugcShortsPrompt'
import {
  ugcShortsTemplateApi,
  type ShortsTemplateRequest,
  type ShortsTemplateResponse,
} from '@/api/ugcShortsTemplate'
import { useWorkspaceStore } from '@/stores/workspace'

export const useUgcShortsStore = defineStore('ugcShorts', () => {
  const workspaceStore = useWorkspaceStore()

  const prompts = ref<ShortsPromptResponse[]>([])
  const promptsLoading = ref(false)
  const promptsLoadError = ref<string | null>(null)
  const revisions = ref<ShortsPromptRevisionResponse[]>([])
  const revisionsLoading = ref(false)

  const templates = ref<ShortsTemplateResponse[]>([])
  const templatesLoading = ref(false)
  const templatesLoadError = ref<string | null>(null)

  async function requireWorkspaceId(): Promise<number> {
    const id = await workspaceStore.ensureActiveWorkspace()
    if (id == null) {
      throw new Error('활성 워크스페이스가 없습니다. 먼저 워크스페이스를 선택하세요.')
    }
    return id
  }

  /** 목록 내 같은 stage 의 프롬프트를 최신 응답으로 교체 */
  function replacePrompt(updated: ShortsPromptResponse) {
    const idx = prompts.value.findIndex((p) => p.stage === updated.stage)
    if (idx >= 0) prompts.value[idx] = updated
  }

  /** 목록 내 같은 id 의 템플릿을 최신 응답으로 교체 */
  function replaceTemplate(updated: ShortsTemplateResponse) {
    const idx = templates.value.findIndex((t) => t.id === updated.id)
    if (idx >= 0) templates.value[idx] = updated
  }

  async function fetchPrompts() {
    promptsLoading.value = true
    promptsLoadError.value = null
    try {
      const id = await workspaceStore.ensureActiveWorkspace()
      if (id == null) {
        prompts.value = []
        return
      }
      prompts.value = await ugcShortsPromptApi.list(id)
    } catch (error) {
      promptsLoadError.value = error instanceof Error ? error.message : '쇼츠 프롬프트를 불러오지 못했습니다.'
      throw error
    } finally {
      promptsLoading.value = false
    }
  }

  async function updatePrompt(stage: PipelineStage, request: UpdateShortsPromptRequest) {
    const updated = await ugcShortsPromptApi.update(await requireWorkspaceId(), stage, request)
    replacePrompt(updated)
    return updated
  }

  /** 오버라이드 삭제 = 시스템 기본값 복원 */
  async function resetPrompt(stage: PipelineStage) {
    const updated = await ugcShortsPromptApi.resetToDefault(await requireWorkspaceId(), stage)
    replacePrompt(updated)
    return updated
  }

  async function fetchRevisions(stage: PipelineStage) {
    revisionsLoading.value = true
    try {
      revisions.value = await ugcShortsPromptApi.revisions(await requireWorkspaceId(), stage)
    } finally {
      revisionsLoading.value = false
    }
  }

  async function restoreRevision(stage: PipelineStage, revision: number) {
    const updated = await ugcShortsPromptApi.restoreRevision(await requireWorkspaceId(), stage, revision)
    replacePrompt(updated)
    return updated
  }

  async function fetchTemplates() {
    templatesLoading.value = true
    templatesLoadError.value = null
    try {
      const id = await workspaceStore.ensureActiveWorkspace()
      if (id == null) {
        templates.value = []
        return
      }
      templates.value = await ugcShortsTemplateApi.list(id)
    } catch (error) {
      templatesLoadError.value = error instanceof Error ? error.message : '쇼츠 템플릿을 불러오지 못했습니다.'
      throw error
    } finally {
      templatesLoading.value = false
    }
  }

  async function createTemplate(request: ShortsTemplateRequest) {
    const created = await ugcShortsTemplateApi.create(await requireWorkspaceId(), request)
    templates.value.push(created)
    return created
  }

  async function updateTemplate(templateId: number, request: ShortsTemplateRequest) {
    const updated = await ugcShortsTemplateApi.update(await requireWorkspaceId(), templateId, request)
    replaceTemplate(updated)
    return updated
  }

  async function deleteTemplate(templateId: number) {
    await ugcShortsTemplateApi.remove(await requireWorkspaceId(), templateId)
    templates.value = templates.value.filter((t) => t.id !== templateId)
  }

  async function uploadReferenceImage(templateId: number, file: File) {
    const updated = await ugcShortsTemplateApi.uploadReferenceImage(
      await requireWorkspaceId(),
      templateId,
      file,
    )
    replaceTemplate(updated)
    return updated
  }

  return {
    prompts,
    promptsLoading,
    promptsLoadError,
    revisions,
    revisionsLoading,
    templates,
    templatesLoading,
    templatesLoadError,
    fetchPrompts,
    updatePrompt,
    resetPrompt,
    fetchRevisions,
    restoreRevision,
    fetchTemplates,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    uploadReferenceImage,
  }
})
