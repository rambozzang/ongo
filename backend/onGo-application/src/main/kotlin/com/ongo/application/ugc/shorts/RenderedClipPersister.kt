package com.ongo.application.ugc.shorts

import com.ongo.application.common.FileStoragePort
import com.ongo.application.common.StorageObjectCleanup
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 렌더가 끝난 클립을 영상 레코드로 확정한다. **업로드된 오브젝트의 소유권이 넘어오는 지점**이다.
 *
 * 렌더와 업로드는 여기 들어오지 않는다. 인코딩은 분 단위 CPU 작업이고 업로드는 수백 MB 를
 * 밀어 올리는 I/O 라, 그걸 트랜잭션으로 감싸면 그 시간 내내 DB 커넥션을 붙들어 커넥션 풀이
 * 마른다. 트랜잭션이 필요한 구간은 "영상 행을 만들고 → 클립에 연결하고 → job 을 완료로
 * 표시한다" 셋이 함께 성립해야 한다는 것뿐이다.
 *
 * 별도 빈인 이유는 `@Transactional` 이 프록시로 동작하기 때문이다. 비동기 `runRender` 안의
 * private 메서드에 애노테이션을 달면 자기호출이라 프록시를 타지 않아 트랜잭션이 아예 생기지
 * 않는다. 그러면 세 저장이 각각 커밋돼 중간 실패 시 클립만 RENDERED 이고 job 은 미완인
 * 어중간한 상태가 남는다.
 *
 * 실패 시 오브젝트 정리는 [StorageObjectCleanup] 이 맡는다. 커밋은 이 메서드가 반환된 뒤
 * 일어나므로 catch 만으로는 커밋 실패를 덮을 수 없어 롤백 콜백을 함께 건다.
 */
@Service
class RenderedClipPersister(
    private val videoRepository: VideoRepository,
    private val clipRepository: ShortsClipRepository,
    private val stateService: ShortsRenderJobStateService,
    private val fileStoragePort: FileStoragePort,
    /** 퍼널 계측 통로. 기록은 커밋 뒤 리스너가 맡는다 — 여기서 직접 쓰지 않는다. */
    private val eventPublisher: ApplicationEventPublisher,
) {

    /**
     * @param objectKey 이미 업로드를 마친 오브젝트 키. 확정에 실패하면 이 키만 지운다 —
     *        `shorts/run-*` 프리픽스를 통째로 지우면 같은 실행의 **성공한** 다른 클립까지
     *        날아간다. 그건 사용자의 영구 영상 자산이다.
     */
    @Transactional
    fun persist(
        jobId: String,
        clip: ShortsClip,
        userId: Long,
        objectKey: String,
        fileUrl: String,
        sizeBytes: Long,
    ): Long {
        val cleanup = StorageObjectCleanup(fileStoragePort, objectKey)
        cleanup.deleteIfTransactionRollsBack()

        try {
            val video = videoRepository.save(
                Video(
                    userId = userId,
                    title = clip.title ?: "쇼츠 클립 ${clip.seq}",
                    fileUrl = fileUrl,
                    storageObjectKey = objectKey,
                    fileSizeBytes = sizeBytes,
                    originalFilename = "clip-${clip.seq}.mp4",
                    status = UploadStatus.DRAFT,
                ),
            )
            val videoId = video.id ?: throw IllegalStateException("영상 레코드를 만들지 못했습니다")

            // 업로드만으로는 끝이 아니다. 클립에 연결해야 게시 대상이 된다.
            clipRepository.update(clip.copy(renderedVideoId = videoId, status = ClipStatus.RENDERED))
            stateService.markCompleted(jobId, videoId)

            /*
             * 세 저장이 **모두** 성공한 뒤에만 발행한다.
             *
             * 위 어느 줄이든 던지면 아래 catch 가 오브젝트를 지우고 예외를 다시 던지므로
             * 이 줄에 도달하지 못한다. 즉 연결되지 않은 클립이 가용으로 기록될 수 없다.
             *
             * 발행은 트랜잭션 안이지만 **소비는 커밋 뒤**다(AFTER_COMMIT). 롤백되면
             * 이벤트도 없던 일이 되고, 커밋되면 기록 실패가 결과물에 닿지 못한다.
             * 그래서 여기서 활동 로그를 직접 쓰지 않는다 — 기록 실패가 이 트랜잭션을
             * 롤백시키면 방금 만든 영상과 연결이 통째로 사라진다.
             */
            eventPublisher.publishEvent(
                ShortsClipAvailableEvent(userId = userId, runId = clip.runId, clipId = clip.id),
            )
            return videoId
        } catch (e: Exception) {
            cleanup.deleteOnce()
            throw e
        }
    }
}
