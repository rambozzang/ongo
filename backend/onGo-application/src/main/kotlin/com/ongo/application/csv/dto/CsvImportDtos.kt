package com.ongo.application.csv.dto

data class CsvImportResponse(
    val totalRows: Int,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<CsvRowError>,
)

data class CsvRowError(
    val rowNumber: Int,
    val message: String,
)
