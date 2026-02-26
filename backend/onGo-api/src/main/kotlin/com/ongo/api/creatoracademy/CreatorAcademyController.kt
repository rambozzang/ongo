package com.ongo.api.creatoracademy

import com.ongo.application.creatoracademy.CreatorAcademyUseCase
import com.ongo.application.creatoracademy.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크리에이터 아카데미", description = "크리에이터 교육 과정 및 학습 관리")
@RestController
@RequestMapping("/api/v1/academy")
class CreatorAcademyController(
    private val creatorAcademyUseCase: CreatorAcademyUseCase
) {

    @Operation(summary = "강좌 목록 조회")
    @GetMapping("/courses")
    fun getCourses(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ResData<List<CourseResponse>>> {
        val result = creatorAcademyUseCase.getCourses(userId, category)
        return ResData.success(result)
    }

    @Operation(summary = "강좌 상세 조회")
    @GetMapping("/courses/{id}")
    fun getCourse(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CourseResponse>> {
        val result = creatorAcademyUseCase.getCourse(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "강좌 수강 신청")
    @PostMapping("/enroll")
    fun enroll(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: EnrollRequest,
    ): ResponseEntity<ResData<Nothing?>> {
        creatorAcademyUseCase.enroll(userId, request.courseId)
        return ResData.success(null, "수강 신청이 완료되었습니다")
    }

    @Operation(summary = "레슨 완료 처리")
    @PostMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    fun completeLesson(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable courseId: Long,
        @PathVariable lessonId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        creatorAcademyUseCase.completeLesson(userId, courseId, lessonId)
        return ResData.success(null, "레슨이 완료되었습니다")
    }

    @Operation(summary = "학습 진행 현황 조회")
    @GetMapping("/progress")
    fun getProgress(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<LearningProgressResponse>> {
        val result = creatorAcademyUseCase.getProgress(userId)
        return ResData.success(result)
    }
}
