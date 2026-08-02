package com.ongo.application.ugc.shorts

import com.ongo.application.common.StorageService
import com.ongo.application.ugc.shorts.dto.ShortsTemplateRequest
import com.ongo.application.ugc.shorts.dto.ShortsTemplateResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.FileValidationUtil
import com.ongo.domain.ugc.shorts.ShortsTemplate
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * UGC 쇼츠 템플릿 유스케이스.
 *
 * 인가 원칙: 모든 진입점에서 `userId`가 대상 `workspaceId`에 접근 가능한지 먼저 검증하고,
 * 템플릿이 해당 워크스페이스 소유인지 확인한다. 다른 워크스페이스 자원 접근은 ACCESS_DENIED.
 */
@Service
class ShortsTemplateUseCase(
    private val shortsTemplateRepository: ShortsTemplateRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val storageService: StorageService,
) {

    fun listTemplates(userId: Long, workspaceId: Long): List<ShortsTemplateResponse> {
        assertWorkspaceAccess(userId, workspaceId)
        return shortsTemplateRepository.findByWorkspace(workspaceId).map { it.toResponse() }
    }

    fun getTemplate(userId: Long, workspaceId: Long, templateId: Long): ShortsTemplateResponse =
        loadTemplateInWorkspace(userId, workspaceId, templateId).toResponse()

    @Transactional
    fun createTemplate(
        userId: Long,
        workspaceId: Long,
        request: ShortsTemplateRequest,
    ): ShortsTemplateResponse {
        assertWorkspaceAccess(userId, workspaceId)
        // 새 기본 템플릿 지정 시 같은 워크스페이스의 기존 기본값을 먼저 해제한다
        if (request.isDefault) {
            shortsTemplateRepository.clearDefault(workspaceId)
        }
        val saved = shortsTemplateRepository.save(
            ShortsTemplate(
                workspaceId = workspaceId,
                name = request.name,
                description = request.description,
                aspectRatio = request.aspectRatio,
                width = request.width,
                height = request.height,
                backgroundStyle = request.backgroundStyle,
                hookFontFamily = request.hookFontFamily,
                hookFontSize = request.hookFontSize,
                hookFontColor = request.hookFontColor,
                hookStrokeColor = request.hookStrokeColor,
                hookPosition = request.hookPosition,
                captionFontFamily = request.captionFontFamily,
                captionFontSize = request.captionFontSize,
                captionFontColor = request.captionFontColor,
                captionStrokeColor = request.captionStrokeColor,
                captionPosition = request.captionPosition,
                safeAreaTop = request.safeAreaTop,
                safeAreaBottom = request.safeAreaBottom,
                isDefault = request.isDefault,
                createdBy = userId,
            ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateTemplate(
        userId: Long,
        workspaceId: Long,
        templateId: Long,
        request: ShortsTemplateRequest,
    ): ShortsTemplateResponse {
        val existing = loadTemplateInWorkspace(userId, workspaceId, templateId)
        if (request.isDefault && !existing.isDefault) {
            shortsTemplateRepository.clearDefault(workspaceId)
        }
        val saved = shortsTemplateRepository.update(
            existing.copy(
                name = request.name,
                description = request.description,
                aspectRatio = request.aspectRatio,
                width = request.width,
                height = request.height,
                backgroundStyle = request.backgroundStyle,
                hookFontFamily = request.hookFontFamily,
                hookFontSize = request.hookFontSize,
                hookFontColor = request.hookFontColor,
                hookStrokeColor = request.hookStrokeColor,
                hookPosition = request.hookPosition,
                captionFontFamily = request.captionFontFamily,
                captionFontSize = request.captionFontSize,
                captionFontColor = request.captionFontColor,
                captionStrokeColor = request.captionStrokeColor,
                captionPosition = request.captionPosition,
                safeAreaTop = request.safeAreaTop,
                safeAreaBottom = request.safeAreaBottom,
                isDefault = request.isDefault,
            ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun deleteTemplate(userId: Long, workspaceId: Long, templateId: Long) {
        val existing = loadTemplateInWorkspace(userId, workspaceId, templateId)
        shortsTemplateRepository.delete(existing.id)
    }

    /** 레퍼런스 이미지 업로드. 업로드된 URL을 템플릿에 반영한다. */
    @Transactional
    fun uploadReferenceImage(
        userId: Long,
        workspaceId: Long,
        templateId: Long,
        file: MultipartFile,
    ): ShortsTemplateResponse {
        val existing = loadTemplateInWorkspace(userId, workspaceId, templateId)
        val filename = file.originalFilename ?: "reference.png"
        val contentType = file.contentType ?: "image/png"
        FileValidationUtil.validateImage(filename, contentType, file.size)

        val key = "ugc-shorts/$workspaceId/templates/$templateId/reference_${System.currentTimeMillis()}_$filename"
        val imageUrl = storageService.uploadFile(key, file.inputStream, contentType, file.size)

        val saved = shortsTemplateRepository.update(existing.copy(referenceImageUrl = imageUrl))
        return saved.toResponse()
    }

    // ---- 인가 헬퍼 ----

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadTemplateInWorkspace(userId: Long, workspaceId: Long, templateId: Long): ShortsTemplate {
        assertWorkspaceAccess(userId, workspaceId)
        val template = shortsTemplateRepository.findById(templateId)
            ?: throw BusinessException("SHORTS_TEMPLATE_NOT_FOUND", "템플릿을 찾을 수 없습니다")
        if (template.workspaceId != workspaceId) {
            throw BusinessException("ACCESS_DENIED", "다른 워크스페이스의 템플릿입니다")
        }
        return template
    }

    // ---- 매핑 ----

    private fun ShortsTemplate.toResponse(): ShortsTemplateResponse = ShortsTemplateResponse(
        id = id,
        name = name,
        description = description,
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        backgroundStyle = backgroundStyle,
        hookFontFamily = hookFontFamily,
        hookFontSize = hookFontSize,
        hookFontColor = hookFontColor,
        hookStrokeColor = hookStrokeColor,
        hookPosition = hookPosition,
        captionFontFamily = captionFontFamily,
        captionFontSize = captionFontSize,
        captionFontColor = captionFontColor,
        captionStrokeColor = captionStrokeColor,
        captionPosition = captionPosition,
        safeAreaTop = safeAreaTop,
        safeAreaBottom = safeAreaBottom,
        referenceImageUrl = referenceImageUrl,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
