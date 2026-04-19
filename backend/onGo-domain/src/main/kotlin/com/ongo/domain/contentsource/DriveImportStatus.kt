package com.ongo.domain.contentsource

enum class DriveImportStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELLED;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED || this == CANCELLED
}
