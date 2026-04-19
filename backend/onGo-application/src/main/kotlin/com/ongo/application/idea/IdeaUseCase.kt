package com.ongo.application.idea

import com.ongo.application.ai.GenerateIdeasUseCase
import com.ongo.application.idea.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.idea.Idea
import com.ongo.domain.idea.IdeaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IdeaUseCase(
    private val ideaRepository: IdeaRepository,
    private val generateIdeasUseCase: GenerateIdeasUseCase,
) {

    private val log = LoggerFactory.getLogger(IdeaUseCase::class.java)

    fun listIdeas(userId: Long, status: String?, category: String?, priority: String?): List<IdeaResponse> {
        return ideaRepository.findByUserId(userId, status, category, priority)
            .map { it.toResponse() }
    }

    @Transactional
    fun createIdea(userId: Long, request: CreateIdeaRequest): IdeaResponse {
        val idea = Idea(
            userId = userId,
            title = request.title,
            description = request.description,
            status = request.status,
            category = request.category,
            tags = request.tags.toTypedArray(),
            priority = request.priority,
            source = request.source,
            referenceUrl = request.referenceUrl,
            dueDate = request.dueDate,
        )
        return ideaRepository.save(idea).toResponse()
    }

    @Transactional
    fun updateIdea(userId: Long, ideaId: Long, request: UpdateIdeaRequest): IdeaResponse {
        val idea = ideaRepository.findById(ideaId) ?: throw NotFoundException("아이디어", ideaId)
        if (idea.userId != userId) throw ForbiddenException("해당 아이디어에 대한 권한이 없습니다")

        val updated = idea.copy(
            title = request.title ?: idea.title,
            description = request.description ?: idea.description,
            status = request.status ?: idea.status,
            category = request.category ?: idea.category,
            tags = request.tags?.toTypedArray() ?: idea.tags,
            priority = request.priority ?: idea.priority,
            source = request.source ?: idea.source,
            referenceUrl = request.referenceUrl ?: idea.referenceUrl,
            dueDate = request.dueDate ?: idea.dueDate,
        )
        return ideaRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteIdea(userId: Long, ideaId: Long) {
        val idea = ideaRepository.findById(ideaId) ?: throw NotFoundException("아이디어", ideaId)
        if (idea.userId != userId) throw ForbiddenException("해당 아이디어에 대한 권한이 없습니다")
        ideaRepository.delete(ideaId)
    }

    @Transactional
    fun changeStatus(userId: Long, ideaId: Long, request: ChangeIdeaStatusRequest): IdeaResponse {
        val idea = ideaRepository.findById(ideaId) ?: throw NotFoundException("아이디어", ideaId)
        if (idea.userId != userId) throw ForbiddenException("해당 아이디어에 대한 권한이 없습니다")

        val updated = idea.copy(status = request.status)
        return ideaRepository.update(updated).toResponse()
    }

    @Transactional
    fun generateAiIdeas(userId: Long, category: String?): List<IdeaResponse> {
        // 최근 아이디어 제목을 수집하여 중복 방지
        val recentTitles = ideaRepository.findByUserId(userId, null, null, null)
            .take(10)
            .map { it.title }

        val result = generateIdeasUseCase.execute(
            userId = userId,
            category = category ?: "일반",
            recentTitles = recentTitles,
        )

        // AI가 생성한 아이디어를 DB에 저장
        return result.ideas.map { item ->
            val idea = Idea(
                userId = userId,
                title = item.title,
                description = item.description,
                status = "BACKLOG",
                category = category,
                source = "AI",
            )
            ideaRepository.save(idea).toResponse()
        }
    }

    private fun Idea.toResponse(): IdeaResponse = IdeaResponse(
        id = id!!,
        title = title,
        description = description,
        status = status,
        category = category,
        tags = tags.toList(),
        priority = priority,
        source = source,
        referenceUrl = referenceUrl,
        dueDate = dueDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
