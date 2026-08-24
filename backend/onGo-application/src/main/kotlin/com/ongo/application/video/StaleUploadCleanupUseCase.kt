package com.ongo.application.video

import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 확정되지 않은 채 방치된 업로드를 회수한다.
 *
 * presigned URL 은 60분이면 만료되는데, 그때까지 confirm 이 오지 않은 업로드는 사용자가 더 이상
 * 완료할 수 없다. 그런데 이미 올라간 오브젝트는 스토리지에 남아 계속 과금되고, UPLOADING 행은
 * 아무도 보지 않는 채로 쌓인다. 브라우저를 닫거나 업로드가 끊기기만 해도 생기는 상태라
 * 사용자가 늘수록 그냥 누적된다.
 *
 * 회수 대상은 **UPLOADING 이고 fileUrl 이 없으며 임계 시각보다 오래된 행**뿐이다. DRAFT·게시된
 * 영상, 아직 진행 중일 수 있는 최신 업로드, 다른 사용자의 정상 데이터는 조건에서 제외된다.
 */
@Service
class StaleUploadCleanupUseCase(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
    @Value("\${storage.upload.stale-after-minutes:180}") private val staleAfterMinutes: Long,
    @Value("\${storage.upload.cleanup-batch-size:200}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @param now 기준 시각. 호출부가 넘겨 테스트에서 시간을 고정할 수 있게 한다.
     * @return 회수한 업로드 수
     */
    @Transactional
    fun cleanupStaleUploads(now: LocalDateTime = LocalDateTime.now()): Int {
        // URL 만료(60분)보다 넉넉히 뒤에 걷어야 진행 중인 업로드를 끊지 않는다.
        val threshold = now.minusMinutes(staleAfterMinutes)
        val stale = videoRepository.findStaleUploading(threshold, batchSize)
        if (stale.isEmpty()) return 0

        var reclaimed = 0
        for (video in stale) {
            val videoId = video.id ?: continue
            // 오브젝트를 먼저 지우고, 성공했을 때만 행을 지운다. 삭제가 실패했는데 행을 없애면
            // 과금되는 오브젝트를 가리키는 단서가 사라져 영구 고아가 된다. 남겨두면 다음 주기가
            // 다시 시도한다.
            val storageCleared = runCatching { storageService.deleteFile(videoId) }
                .onFailure { log.error("방치된 업로드 오브젝트 정리 실패 — 행을 남겨 재시도 [videoId={}]", videoId, it) }
                .isSuccess
            if (!storageCleared) continue
            runCatching { videoRepository.delete(videoId) }
                .onSuccess { reclaimed++ }
                .onFailure { log.error("방치된 업로드 행 정리 실패 [videoId={}]", videoId, it) }
        }
        log.info("방치된 업로드 회수 완료: 대상={}, 회수={}, 기준={}", stale.size, reclaimed, threshold)
        return reclaimed
    }
}
