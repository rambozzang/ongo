package com.ongo.domain.ugc.shorts

/**
 * 쇼츠 프롬프트 저장소.
 *
 * 시스템 기본값(workspace_id IS NULL)과 워크스페이스 오버라이드를 함께 다룬다.
 */
interface ShortsPromptRepository {
    fun findDefaults(): List<ShortsPrompt>
    fun findDefaultByStage(stage: PipelineStage): ShortsPrompt?
    fun findByWorkspace(workspaceId: Long): List<ShortsPrompt>
    fun findByWorkspaceAndStage(workspaceId: Long, stage: PipelineStage): ShortsPrompt?
    fun save(prompt: ShortsPrompt): ShortsPrompt
    fun update(prompt: ShortsPrompt): ShortsPrompt
    fun deleteByWorkspaceAndStage(workspaceId: Long, stage: PipelineStage): Boolean
    fun saveRevision(revision: ShortsPromptRevision): ShortsPromptRevision
    fun findRevisions(promptId: Long): List<ShortsPromptRevision>
    fun findRevision(promptId: Long, revision: Int): ShortsPromptRevision?
}
