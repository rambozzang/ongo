package com.ongo.common.exception

class StorageQuotaExceededException(
    val limitBytes: Long,
    val usedBytes: Long,
    val requiredBytes: Long,
) : BusinessException(
    "STORAGE_QUOTA_EXCEEDED",
    "스토리지 한도를 초과했습니다. 한도: ${formatBytes(limitBytes)}, 사용: ${formatBytes(usedBytes)}, 필요: ${formatBytes(requiredBytes)}"
) {
    companion object {
        private fun formatBytes(bytes: Long): String {
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) "%.1f GB".format(gb)
            else "%.0f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
