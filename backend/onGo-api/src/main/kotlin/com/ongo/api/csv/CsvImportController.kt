package com.ongo.api.csv

import com.ongo.api.config.CurrentUser
import com.ongo.application.csv.CsvImportUseCase
import com.ongo.application.csv.dto.CsvImportResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "CSV 가져오기", description = "CSV 파일을 통한 일괄 영상 초안 생성")
@RestController
@RequestMapping("/api/v1/videos/import/csv")
class CsvImportController(
    private val csvImportUseCase: CsvImportUseCase,
) {

    @Operation(summary = "CSV 파일 업로드 및 가져오기", description = "CSV 파일을 업로드하여 일괄로 영상 초안을 생성합니다.")
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importCsv(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ResData<CsvImportResponse>> {
        val result = csvImportUseCase.importCsv(userId, file)
        return ResData.success(result, "${result.successCount}개의 영상 초안이 생성되었습니다")
    }
}
