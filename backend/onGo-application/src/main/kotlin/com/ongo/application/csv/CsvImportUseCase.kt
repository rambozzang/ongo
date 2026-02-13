package com.ongo.application.csv

import com.ongo.application.csv.dto.CsvImportResponse
import com.ongo.application.csv.dto.CsvRowError
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class CsvImportUseCase(
    private val videoRepository: VideoRepository,
) {

    @Transactional
    fun importCsv(userId: Long, file: MultipartFile): CsvImportResponse {
        val reader = BufferedReader(InputStreamReader(file.inputStream, Charsets.UTF_8))
        val lines = reader.readLines().filter { it.isNotBlank() }

        if (lines.size < 2) {
            return CsvImportResponse(totalRows = 0, successCount = 0, errorCount = 0, errors = listOf(
                CsvRowError(0, "데이터가 없습니다. 헤더 행 이후 최소 1개의 데이터 행이 필요합니다.")
            ))
        }

        val dataLines = lines.drop(1) // skip header
        val errors = mutableListOf<CsvRowError>()
        var successCount = 0

        for ((index, line) in dataLines.withIndex()) {
            val rowNumber = index + 2 // 1-indexed + 1 for header
            try {
                val columns = parseCsvLine(line)
                val title = columns.getOrNull(0)?.trim() ?: ""
                val description = columns.getOrNull(1)?.trim() ?: ""
                val tagsRaw = columns.getOrNull(2)?.trim() ?: ""
                val category = columns.getOrNull(3)?.trim() ?: ""
                val platformsRaw = columns.getOrNull(4)?.trim() ?: ""
                val scheduledAtRaw = columns.getOrNull(5)?.trim() ?: ""

                if (title.isBlank()) {
                    errors.add(CsvRowError(rowNumber, "제목은 필수 항목입니다."))
                    continue
                }

                if (platformsRaw.isBlank()) {
                    errors.add(CsvRowError(rowNumber, "플랫폼은 필수 항목입니다."))
                    continue
                }

                val tags = tagsRaw.split(";").map { it.trim() }.filter { it.isNotEmpty() }

                val video = Video(
                    userId = userId,
                    title = title,
                    description = description,
                    tags = tags,
                    category = category.ifBlank { null },
                    status = UploadStatus.DRAFT,
                )
                videoRepository.save(video)
                successCount++
            } catch (e: Exception) {
                errors.add(CsvRowError(rowNumber, "처리 중 오류: ${e.message}"))
            }
        }

        return CsvImportResponse(
            totalRows = dataLines.size,
            successCount = successCount,
            errorCount = errors.size,
            errors = errors,
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val char = line[i]
            if (char == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    // skip next char handled by loop
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
