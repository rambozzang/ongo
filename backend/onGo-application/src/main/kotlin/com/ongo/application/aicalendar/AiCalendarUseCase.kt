package com.ongo.application.aicalendar

import com.ongo.application.aicalendar.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.aicalendar.AiContentCalendar
import com.ongo.domain.aicalendar.AiCalendarRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AiCalendarUseCase(
    private val aiCalendarRepository: AiCalendarRepository,
) {

    fun listCalendars(userId: Long): List<AiCalendarResponse> {
        return aiCalendarRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getCalendar(userId: Long, calendarId: Long): AiCalendarResponse {
        val calendar = aiCalendarRepository.findById(calendarId) ?: throw NotFoundException("캘린더", calendarId)
        if (calendar.userId != userId) throw ForbiddenException("해당 캘린더에 대한 권한이 없습니다")
        return calendar.toResponse()
    }

    @Transactional
    fun generateCalendar(userId: Long, request: GenerateCalendarRequest): AiCalendarResponse {
        val calendar = AiContentCalendar(
            userId = userId,
            title = request.title,
            startDate = request.startDate,
            endDate = request.endDate,
            settings = request.settings,
            calendarData = "[]", // AI will populate later
        )
        return aiCalendarRepository.save(calendar).toResponse()
    }

    @Transactional
    fun updateCalendar(userId: Long, calendarId: Long, request: UpdateCalendarRequest): AiCalendarResponse {
        val calendar = aiCalendarRepository.findById(calendarId) ?: throw NotFoundException("캘린더", calendarId)
        if (calendar.userId != userId) throw ForbiddenException("해당 캘린더에 대한 권한이 없습니다")
        val updated = calendar.copy(
            title = request.title ?: calendar.title,
            calendarData = request.calendarData ?: calendar.calendarData,
            status = request.status ?: calendar.status,
        )
        return aiCalendarRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteCalendar(userId: Long, calendarId: Long) {
        val calendar = aiCalendarRepository.findById(calendarId) ?: throw NotFoundException("캘린더", calendarId)
        if (calendar.userId != userId) throw ForbiddenException("해당 캘린더에 대한 권한이 없습니다")
        aiCalendarRepository.delete(calendarId)
    }

    private fun AiContentCalendar.toResponse() = AiCalendarResponse(
        id = id!!,
        title = title,
        startDate = startDate,
        endDate = endDate,
        settings = settings,
        calendarData = calendarData,
        status = status,
        createdAt = createdAt,
    )
}
