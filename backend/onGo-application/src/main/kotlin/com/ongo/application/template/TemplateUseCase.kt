package com.ongo.application.template

import com.ongo.application.template.dto.CreateTemplateRequest
import com.ongo.application.template.dto.TemplateListResponse
import com.ongo.application.template.dto.TemplateResponse
import com.ongo.application.template.dto.UpdateTemplateRequest
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.template.Template
import com.ongo.domain.template.TemplateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TemplateUseCase(
    private val templateRepository: TemplateRepository,
) {

    fun listTemplates(userId: Long, page: Int, size: Int): TemplateListResponse {
        val templates = templateRepository.findByUserId(userId, page, size)
        val totalCount = templateRepository.countByUserId(userId)
        return TemplateListResponse(
            templates = templates.map { it.toResponse() },
            totalCount = totalCount,
        )
    }

    @Transactional
    fun createTemplate(userId: Long, request: CreateTemplateRequest): TemplateResponse {
        val template = Template(
            userId = userId,
            name = request.name,
            titleTemplate = request.titleTemplate,
            descriptionTemplate = request.descriptionTemplate,
            tags = request.tags,
            category = request.category,
            platform = request.platform,
        )
        return templateRepository.save(template).toResponse()
    }

    @Transactional
    fun updateTemplate(userId: Long, templateId: Long, request: UpdateTemplateRequest): TemplateResponse {
        val template = templateRepository.findById(templateId) ?: throw NotFoundException("템플릿", templateId)
        if (template.userId != userId) throw ForbiddenException("해당 템플릿에 대한 권한이 없습니다")

        val updated = template.copy(
            name = request.name,
            titleTemplate = request.titleTemplate,
            descriptionTemplate = request.descriptionTemplate,
            tags = request.tags,
            category = request.category,
            platform = request.platform,
        )
        return templateRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteTemplate(userId: Long, templateId: Long) {
        val template = templateRepository.findById(templateId) ?: throw NotFoundException("템플릿", templateId)
        if (template.userId != userId) throw ForbiddenException("해당 템플릿에 대한 권한이 없습니다")
        templateRepository.delete(templateId)
    }

    @Transactional
    fun useTemplate(userId: Long, templateId: Long): TemplateResponse {
        val template = templateRepository.findById(templateId) ?: throw NotFoundException("템플릿", templateId)
        if (template.userId != userId) throw ForbiddenException("해당 템플릿에 대한 권한이 없습니다")
        templateRepository.incrementUsageCount(templateId)
        val updated = templateRepository.findById(templateId)!!
        return updated.toResponse()
    }

    private fun Template.toResponse(): TemplateResponse = TemplateResponse(
        id = id!!,
        name = name,
        titleTemplate = titleTemplate,
        descriptionTemplate = descriptionTemplate,
        tags = tags,
        category = category,
        platform = platform,
        usageCount = usageCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
