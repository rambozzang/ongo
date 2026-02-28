package com.ongo.application.thumbnailgenerator

import com.ongo.application.thumbnailgenerator.dto.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ThumbnailGeneratorUseCase {

    fun getTemplates(userId: Long): ThumbnailTemplatesWrapper {
        val templates = listOf(
            ThumbnailTemplateResponse(
                id = 1, name = "볼드 텍스트", style = "BOLD_TEXT",
                previewUrl = "/thumbnails/templates/bold-text-preview.jpg", popularity = 95,
            ),
            ThumbnailTemplateResponse(
                id = 2, name = "미니멀리스트", style = "MINIMALIST",
                previewUrl = "/thumbnails/templates/minimalist-preview.jpg", popularity = 82,
            ),
            ThumbnailTemplateResponse(
                id = 3, name = "콜라주", style = "COLLAGE",
                previewUrl = "/thumbnails/templates/collage-preview.jpg", popularity = 70,
            ),
            ThumbnailTemplateResponse(
                id = 4, name = "얼굴 포커스", style = "FACE_FOCUS",
                previewUrl = "/thumbnails/templates/face-focus-preview.jpg", popularity = 88,
            ),
            ThumbnailTemplateResponse(
                id = 5, name = "시네마틱", style = "CINEMATIC",
                previewUrl = "/thumbnails/templates/cinematic-preview.jpg", popularity = 76,
            ),
            ThumbnailTemplateResponse(
                id = 6, name = "클릭베이트", style = "CLICKBAIT",
                previewUrl = "/thumbnails/templates/clickbait-preview.jpg", popularity = 91,
            ),
        )
        return ThumbnailTemplatesWrapper(templates)
    }

    fun generate(userId: Long, request: ThumbnailGenerateRequest): ThumbnailGenerateResponse {
        // AI 썸네일 생성 연동 포인트 - Phase 1에서는 mock 데이터 반환
        val now = LocalDateTime.now().toString()
        val thumbnails = listOf(
            GeneratedThumbnailResponse(
                id = 101, imageUrl = "/thumbnails/generated/thumb-101.jpg",
                style = request.style, ctrPrediction = 7.2,
                prompt = "${request.videoTitle} - ${request.style} 스타일", createdAt = now,
            ),
            GeneratedThumbnailResponse(
                id = 102, imageUrl = "/thumbnails/generated/thumb-102.jpg",
                style = request.style, ctrPrediction = 6.8,
                prompt = "${request.videoTitle} - ${request.style} 스타일 변형", createdAt = now,
            ),
            GeneratedThumbnailResponse(
                id = 103, imageUrl = "/thumbnails/generated/thumb-103.jpg",
                style = request.style, ctrPrediction = 8.1,
                prompt = "${request.videoTitle} - ${request.style} 스타일 최적화", createdAt = now,
            ),
        )
        return ThumbnailGenerateResponse(
            thumbnails = thumbnails,
            creditsUsed = 5,
            creditsRemaining = 95,
        )
    }

    fun getHistory(userId: Long): ThumbnailHistoryWrapper {
        val now = LocalDateTime.now().toString()
        val history = listOf(
            ThumbnailHistoryResponse(
                id = 1, videoTitle = "유튜브 성장 비법 공개",
                thumbnails = listOf(
                    GeneratedThumbnailResponse(
                        id = 201, imageUrl = "/thumbnails/generated/thumb-201.jpg",
                        style = "BOLD_TEXT", ctrPrediction = 7.5,
                        prompt = "유튜브 성장 비법 공개 - BOLD_TEXT 스타일", createdAt = now,
                    ),
                ),
                selectedThumbnailId = 201, createdAt = now,
            ),
            ThumbnailHistoryResponse(
                id = 2, videoTitle = "일주일 만에 구독자 1만명",
                thumbnails = listOf(
                    GeneratedThumbnailResponse(
                        id = 202, imageUrl = "/thumbnails/generated/thumb-202.jpg",
                        style = "CLICKBAIT", ctrPrediction = 9.1,
                        prompt = "일주일 만에 구독자 1만명 - CLICKBAIT 스타일", createdAt = now,
                    ),
                ),
                selectedThumbnailId = null, createdAt = now,
            ),
        )
        return ThumbnailHistoryWrapper(history)
    }

    fun selectThumbnail(userId: Long, historyId: Long, thumbnailId: Long) {
        // DB 업데이트 연동 포인트 - Phase 1에서는 no-op
    }
}
