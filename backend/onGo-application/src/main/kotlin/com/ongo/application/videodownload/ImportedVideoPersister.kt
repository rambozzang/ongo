package com.ongo.application.videodownload

import com.fasterxml.jackson.databind.JsonNode
import com.ongo.application.common.FileStoragePort
import com.ongo.application.common.StorageObjectCleanup
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.domain.contentsource.VideoSource
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

/**
 * 가져온 영상을 저장소와 DB 에 확정하는 구간. **한도 판정부터 행 저장까지 한 트랜잭션**이다.
 *
 * 별도 빈으로 떼어낸 이유가 있다. `@Transactional` 은 프록시로 동작해서 같은 클래스 안에서
 * 자기 메서드를 부르면 적용되지 않는다. 다운로드를 담당하는 use case 안에 private 메서드로
 * 두면 애노테이션이 있어도 트랜잭션이 생기지 않고, 그러면 checkQuota 가 잡은 사용자 행 잠금이
 * 검사 직후 커밋과 함께 풀려 동시 요청 둘이 모두 한도를 통과한다 — 고치려던 결함이 그대로 남는다.
 *
 * 다운로드는 여기에 넣지 않는다. 2GB 를 몇 분에 걸쳐 받는 동안 DB 커넥션과 사용자 행 잠금을
 * 붙들고 있으면 같은 사용자의 다른 요청이 전부 막히고 커넥션 풀도 마른다. 잠금이 필요한 구간은
 * "얼마나 쓰고 있는지 읽고 → 올리고 → 행으로 확정한다" 뿐이다.
 */
@Service
class ImportedVideoPersister(
    private val videoRepository: VideoRepository,
    private val fileStoragePort: FileStoragePort,
    private val storageQuotaUseCase: StorageQuotaUseCase,
) {

    /**
     * @param title, originalFilename 호출부에서 길이 제한까지 마친 값. 저장 규칙을 두 곳에 두지 않는다.
     * @param openStream 업로드할 원본을 여는 함수. 스트림 수명을 이 트랜잭션 안으로 가둔다.
     */
    @Transactional
    fun persist(
        userId: Long,
        downloaded: DownloadedVideo,
        objectKey: String,
        title: String,
        originalFilename: String,
        sourceReference: JsonNode,
        openStream: () -> InputStream,
    ): Video {
        // 잠금은 이 트랜잭션이 끝날 때 풀린다. 아래 업로드·저장이 같은 트랜잭션 안에 있어야
        // 검사와 확정 사이에 다른 요청이 끼어들지 못한다.
        storageQuotaUseCase.checkQuota(userId, downloaded.size)

        /*
         * 정리 담당을 하나로 모은다.
         *
         * 트랜잭션 커밋은 이 메서드가 **반환된 뒤** 프록시에서 일어난다. 그래서 커밋이 실패하면
         * 행은 롤백되는데 메서드 안의 try/catch 는 이미 지나가 버려 오브젝트만 남는다. 롤백
         * 동기화 콜백을 걸어야 그 경로까지 덮인다.
         *
         * 두 경로가 겹칠 수 있으므로(메서드 안에서 던진 예외는 catch 도 타고 롤백도 탄다)
         * 삭제 자체를 멱등하게 만든다. 결과적으로 일반 실패·커밋 실패 모두 정확히 한 번,
         * 커밋 성공에는 0번이 된다.
         */
        val cleanup = StorageObjectCleanup(fileStoragePort, objectKey)
        cleanup.deleteIfTransactionRollsBack()

        val fileUrl = openStream().use { input ->
            try {
                fileStoragePort.uploadByKey(objectKey, input, downloaded.contentType, downloaded.size)
            } catch (e: Exception) {
                cleanup.deleteOnce()
                throw BusinessException("VIDEO_STORAGE_UPLOAD_FAILED", "영상 저장에 실패했습니다.")
            }
        }

        return try {
            videoRepository.save(
                Video(
                    userId = userId,
                    title = title,
                    fileUrl = fileUrl,
                    storageObjectKey = objectKey,
                    fileSizeBytes = downloaded.size,
                    originalFilename = originalFilename,
                    mediaType = MediaType.VIDEO,
                    status = UploadStatus.DRAFT,
                    source = VideoSource.URL_IMPORT,
                    sourceReference = sourceReference,
                )
            )
        } catch (e: Exception) {
            // DB 쓰기가 실패했는데 오브젝트를 남기면 아무도 못 찾는 고아가 된다.
            cleanup.deleteOnce()
            throw e
        }
    }
}
