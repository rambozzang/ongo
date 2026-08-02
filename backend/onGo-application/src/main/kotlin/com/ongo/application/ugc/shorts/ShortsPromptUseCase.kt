package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPromptResponse
import com.ongo.application.ugc.shorts.dto.ShortsPromptRevisionResponse
import com.ongo.application.ugc.shorts.dto.UpdateShortsPromptRequest
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPrompt
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import com.ongo.domain.ugc.shorts.ShortsPromptRevision
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * UGC 쇼츠 단계별 프롬프트 유스케이스.
 *
 * 조회는 워크스페이스 오버라이드가 있으면 그것을, 없으면 시스템 기본값(workspace_id IS NULL)을
 * 반환한다. 편집은 오버라이드를 생성/갱신하며 개정 번호를 1 올리고, 변경 전 내용을 개정 이력에
 * 남긴다. 기본값 복원은 오버라이드 행 삭제로 구현한다.
 */
@Service
class ShortsPromptUseCase(
    private val shortsPromptRepository: ShortsPromptRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    /** 9단계 전체를 sortOrder 순으로 반환한다. */
    fun listPrompts(userId: Long, workspaceId: Long): List<ShortsPromptResponse> {
        assertWorkspaceAccess(userId, workspaceId)
        val overrides = shortsPromptRepository.findByWorkspace(workspaceId).associateBy { it.stage }
        return PipelineStage.entries.sortedBy { it.sortOrder }.map { stage ->
            toResponse(overrides[stage], loadDefault(stage))
        }
    }

    fun getPrompt(userId: Long, workspaceId: Long, stageName: String): ShortsPromptResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val stage = parseStage(stageName)
        return toResponse(
            shortsPromptRepository.findByWorkspaceAndStage(workspaceId, stage),
            loadDefault(stage),
        )
    }

    /**
     * 프롬프트 편집. 워크스페이스 오버라이드를 생성하거나 갱신하고 revision을 1 올린다.
     * 변경 전 내용은 개정 이력에 남긴다.
     */
    @Transactional
    fun updatePrompt(
        userId: Long,
        workspaceId: Long,
        stageName: String,
        request: UpdateShortsPromptRequest,
    ): ShortsPromptResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val stage = parseStage(stageName)
        val default = loadDefault(stage)
        val existing = shortsPromptRepository.findByWorkspaceAndStage(workspaceId, stage)

        val saved = if (existing != null) {
            // 변경 전 내용을 개정 이력에 남긴다
            shortsPromptRepository.saveRevision(
                ShortsPromptRevision(
                    promptId = existing.id,
                    revision = existing.revision,
                    systemPrompt = existing.systemPrompt,
                    userPrompt = existing.userPrompt,
                    changeNote = request.changeNote,
                    changedBy = userId,
                ),
            )
            shortsPromptRepository.update(
                existing.copy(
                    systemPrompt = request.systemPrompt,
                    userPrompt = request.userPrompt,
                    revision = existing.revision + 1,
                ),
            )
        } else {
            // 최초 오버라이드: 직전 상태(시스템 기본값)를 개정 1로 남기고 다음 개정으로 시작한다
            val created = shortsPromptRepository.save(
                ShortsPrompt(
                    workspaceId = workspaceId,
                    stage = stage,
                    name = default.name,
                    description = default.description,
                    systemPrompt = request.systemPrompt,
                    userPrompt = request.userPrompt,
                    executable = default.executable,
                    revision = default.revision + 1,
                    createdBy = userId,
                ),
            )
            shortsPromptRepository.saveRevision(
                ShortsPromptRevision(
                    promptId = created.id,
                    revision = default.revision,
                    systemPrompt = default.systemPrompt,
                    userPrompt = default.userPrompt,
                    changeNote = request.changeNote,
                    changedBy = userId,
                ),
            )
            created
        }
        return toResponse(saved, default)
    }

    /** 기본값 복원. 워크스페이스 오버라이드 행을 삭제한다. */
    @Transactional
    fun resetPrompt(userId: Long, workspaceId: Long, stageName: String): ShortsPromptResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val stage = parseStage(stageName)
        val deleted = shortsPromptRepository.deleteByWorkspaceAndStage(workspaceId, stage)
        if (!deleted) {
            throw BusinessException("SHORTS_PROMPT_NOT_CUSTOMIZED", "복원할 워크스페이스 커스텀 프롬프트가 없습니다")
        }
        return toResponse(null, loadDefault(stage))
    }

    /** 개정 이력을 최신순으로 반환한다. 오버라이드가 없으면 빈 목록. */
    fun listRevisions(userId: Long, workspaceId: Long, stageName: String): List<ShortsPromptRevisionResponse> {
        assertWorkspaceAccess(userId, workspaceId)
        val stage = parseStage(stageName)
        val override = shortsPromptRepository.findByWorkspaceAndStage(workspaceId, stage)
            ?: return emptyList()
        return shortsPromptRepository.findRevisions(override.id).map { it.toResponse() }
    }

    /**
     * 개정 롤백. 지정 개정의 내용으로 새 개정을 만든다. 과거 개정은 지우지 않는다.
     */
    @Transactional
    fun restoreRevision(
        userId: Long,
        workspaceId: Long,
        stageName: String,
        revision: Int,
    ): ShortsPromptResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val stage = parseStage(stageName)
        val override = shortsPromptRepository.findByWorkspaceAndStage(workspaceId, stage)
            ?: throw BusinessException("SHORTS_PROMPT_NOT_CUSTOMIZED", "워크스페이스 커스텀 프롬프트가 없습니다")
        val target = shortsPromptRepository.findRevision(override.id, revision)
            ?: throw BusinessException("SHORTS_PROMPT_REVISION_NOT_FOUND", "개정 $revision 을 찾을 수 없습니다")

        // 현재 내용을 개정 이력에 남기고, 지정 개정의 내용으로 새 개정을 만든다
        shortsPromptRepository.saveRevision(
            ShortsPromptRevision(
                promptId = override.id,
                revision = override.revision,
                systemPrompt = override.systemPrompt,
                userPrompt = override.userPrompt,
                changeNote = "개정 $revision 으로 롤백",
                changedBy = userId,
            ),
        )
        val saved = shortsPromptRepository.update(
            override.copy(
                systemPrompt = target.systemPrompt,
                userPrompt = target.userPrompt,
                revision = override.revision + 1,
            ),
        )
        return toResponse(saved, loadDefault(stage))
    }

    // ---- 인가 헬퍼 ----

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun parseStage(stageName: String): PipelineStage =
        runCatching { PipelineStage.valueOf(stageName.uppercase()) }.getOrElse {
            throw BusinessException("SHORTS_PROMPT_STAGE_INVALID", "알 수 없는 파이프라인 단계입니다: $stageName")
        }

    /** 시스템 기본값을 읽되, DB에 없으면 폴팩 상수를 사용한다. */
    private fun loadDefault(stage: PipelineStage): ShortsPrompt =
        shortsPromptRepository.findDefaultByStage(stage) ?: ShortsPromptDefaults.fallback(stage)

    // ---- 매핑 ----

    private fun toResponse(override: ShortsPrompt?, default: ShortsPrompt): ShortsPromptResponse {
        val effective = override ?: default
        return ShortsPromptResponse(
            id = effective.id,
            stage = effective.stage.name,
            name = effective.name,
            description = effective.description,
            systemPrompt = effective.systemPrompt,
            userPrompt = effective.userPrompt,
            executable = effective.executable,
            revision = effective.revision,
            customized = override != null,
            defaultSystemPrompt = default.systemPrompt,
            defaultUserPrompt = default.userPrompt,
            updatedAt = effective.updatedAt,
        )
    }

    private fun ShortsPromptRevision.toResponse(): ShortsPromptRevisionResponse =
        ShortsPromptRevisionResponse(
            revision = revision,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            changeNote = changeNote,
            changedBy = changedBy,
            createdAt = createdAt,
        )
}
