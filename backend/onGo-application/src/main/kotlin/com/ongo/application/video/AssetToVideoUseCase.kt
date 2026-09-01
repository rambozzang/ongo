package com.ongo.application.video

import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** 승격 결과. 화면은 이 id 로 기존 작성 흐름에 진입한다. */
data class PromotedVideo(val videoId: Long)

/**
 * 승격할 수 없는 에셋. **사용자에게 그대로 보여줄 수 있는 문장**만 담는다.
 *
 * 거절 사유가 서로 다른데 예외 하나로 뭉뜽그리면 화면이 "왜 안 되는지"를 말하지 못한다.
 */
class AssetNotPromotableException(message: String) :
    BusinessException("ASSET_NOT_PROMOTABLE", message)

/**
 * 라이브러리의 영상 에셋을 **작성 가능한 영상 초안으로 승격**한다.
 *
 * ## 왜 이 경로가 필요한가
 *
 * `videos` 는 이 제품의 공통 화폐다 — 작성 화면이 만들고, 쇼츠가 소비하고, UGC 제출이
 * 참조한다. 반면 `assets` 는 어디에도 연결되지 않아, 사용자는 유료 저장공간을 쓰면서
 * 그 파일을 아무 데도 쓸 수 없었다. 에셋을 `videos` 행으로 한 번 승격시키면 게시·예약·
 * 쇼츠·재게시가 **추가 배선 없이** 전부 열린다.
 *
 * ## 참조가 아니라 복사다
 *
 * 같은 오브젝트 키를 두 행이 가리키게 하면 저장공간은 아끼지만 수명주기가 얽힌다.
 * 에셋을 정리한 사용자가 이미 게시한 영상까지 잃는데, 지우는 시점에는 그 사실이 보이지
 * 않는다. 되돌릴 수 없는 손실이라 아끼는 쪽을 택하지 않는다. 사본은 반드시
 * `videos/{새 id}/` 아래에 두어 삭제 정책이 서로를 건드리지 않게 한다.
 *
 * 복사한 만큼 저장공간을 실제로 더 쓰므로 **원본 크기만큼 쿼터를 추가 차감**한다.
 * 복사 전에 검사해야 한 바이트도 낭비하지 않고 거절할 수 있다.
 *
 * ## 실패해도 고아를 남기지 않는다
 *
 * 트랜잭션은 DB 행만 되돌린다. 스토리지는 트랜잭션 밖이라 사본이 그대로 남고, 그것을
 * 가리키던 행은 사라진다. 그래서 대상 키를 **복사 전에** 등록해 두고 두 경로 모두에서
 * 되돌린다 — 이 메서드가 던진 경우는 `catch`, 커밋 자체가 실패한 경우는 `afterCompletion`.
 * `VideoQueryUseCase.uploadContentImages` 가 같은 패턴을 쓴다.
 */
@Service
class AssetToVideoUseCase(
    private val assetRepository: AssetRepository,
    private val videoRepository: VideoRepository,
    private val storageQuotaUseCase: StorageQuotaUseCase,
    private val fileStoragePort: FileStoragePort,
    private val userWriteGuard: UserWriteGuard,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun promote(userId: Long, assetId: Long): PromotedVideo {
        userWriteGuard.requireWritable(userId)

        val asset = assetRepository.findById(assetId) ?: throw NotFoundException("에셋", assetId)
        if (asset.userId != userId) throw ForbiddenException("해당 에셋에 대한 권한이 없습니다")

        val sourceKey = validateAndResolveSourceKey(asset)
        val sizeBytes = asset.fileSizeBytes?.takeIf { it > 0 }
            ?: throw AssetNotPromotableException("파일 크기를 확인할 수 없는 에셋은 사용할 수 없습니다.")

        /*
         * 복사는 저장공간을 실제로 한 벌 더 쓴다. 한 바이트도 옮기기 전에 거절해야
         * 한도를 넘긴 사용자에게 고아 사본을 남기지 않는다.
         *
         * 검사와 행 저장이 같은 트랜잭션에 있어야 checkQuota 가 잡는 사용자 행 잠금이
         * 의미를 갖는다 — 그 사이 다른 요청이 끼어들지 못한다.
         */
        storageQuotaUseCase.checkQuota(userId, sizeBytes)

        /*
         * 사본 키에 새 영상 id 가 들어가야 하므로 행을 먼저 만든다. 이 시점의 행은
         * `file_url` 이 없어 쿼터 합계에도, 예약분에도 잡히지 않는다 — 같은 트랜잭션
         * 안에서 아래 갱신까지 끝나므로 커밋된 상태는 항상 정합하다.
         */
        val draft = videoRepository.save(
            Video(
                userId = userId,
                title = draftTitle(asset),
                originalFilename = asset.originalFilename ?: asset.filename,
                fileSizeBytes = sizeBytes,
                mediaType = MediaType.VIDEO,
                status = UploadStatus.DRAFT,
            ),
        )
        val videoId = requireNotNull(draft.id) { "영상 초안 생성에 실패했습니다." }

        val targetKey = "videos/$videoId/${safeObjectName(asset)}"
        val copiedKeys = mutableListOf<String>()
        registerStorageRollback(videoId, copiedKeys)

        try {
            // 키를 먼저 등록한다. 복사 후처리(URL 발급)가 던져도 이미 만들어진 사본을 되돌린다.
            copiedKeys += targetKey
            val fileUrl = fileStoragePort.copyByKey(sourceKey, targetKey)
            videoRepository.update(
                draft.copy(
                    fileUrl = fileUrl,
                    storageObjectKey = targetKey,
                ),
            )
        } catch (e: Throwable) {
            discardCopiedObjects(videoId, copiedKeys)
            throw e
        }

        log.info(
            "에셋을 영상 초안으로 승격했다. userId={} assetId={} videoId={} sourceKey={} targetKey={}",
            userId, assetId, videoId, sourceKey, targetKey,
        )
        return PromotedVideo(videoId = videoId)
    }

    /**
     * 승격 가능한 에셋인지 보고 원본 키를 돌려준다.
     *
     * **키를 추측하지 않는다.** `assets.file_url` 은 서명이 붙은 URL 이고 경로 형식도
     * 어댑터마다 달라, 되짚은 키는 빗나갈 수 있다. 빗나간 키로 복사하면 남의 오브젝트를
     * 사용자 영상으로 만들어 준다. V112 이전 행은 그래서 거절한다 — 사람이 다시 올리면 된다.
     */
    private fun validateAndResolveSourceKey(asset: Asset): String {
        if (asset.fileType != VIDEO_FILE_TYPE) {
            throw AssetNotPromotableException("영상 에셋만 콘텐츠로 만들 수 있습니다.")
        }
        return asset.storageObjectKey?.takeIf { it.isNotBlank() }
            ?: throw AssetNotPromotableException(
                "저장 위치를 확인할 수 없는 예전 에셋입니다. 파일을 다시 올린 뒤 사용해 주세요.",
            )
    }

    /** 제목은 확장자를 뗀 원본 파일명. 비어 있으면 사람이 읽을 기본값을 쓴다. */
    private fun draftTitle(asset: Asset): String =
        (asset.originalFilename ?: asset.filename)
            .substringBeforeLast('.')
            .trim()
            .ifBlank { DEFAULT_TITLE }
            .take(MAX_TITLE_LENGTH)

    /**
     * 사본의 파일명. 스토리지 어댑터가 키를 `^[a-zA-Z0-9\-_./]+$` 로 검사하므로
     * (`S3StorageClient.validateStorageKey`), 한글·공백이 섞인 원본명을 그대로 쓰면
     * URL 발급 단계에서 터진다. 경로 조작 문자도 여기서 함께 걸러진다.
     */
    private fun safeObjectName(asset: Asset): String =
        asset.filename
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .takeLast(MAX_OBJECT_NAME_LENGTH)
            .ifBlank { DEFAULT_OBJECT_NAME }

    /**
     * 커밋이 실패한 경우의 보상. 여기까지 예외 없이 왔어도 커밋 자체는 실패할 수 있고,
     * 그때 행은 사라지는데 사본은 남는다.
     */
    private fun registerStorageRollback(videoId: Long, copiedKeys: MutableList<String>) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        discardCopiedObjects(videoId, copiedKeys)
                    }
                }
            },
        )
    }

    /**
     * 만들어진 사본을 되돌린다. **지운 키만 목록에서 빼고, 못 지운 키는 남긴다.**
     *
     * ## 왜 성공한 키만 빼는가
     *
     * 이 메서드는 두 번 불릴 수 있다 — 이 메서드가 던졌을 때의 `catch`, 그리고 그 예외로
     * 트랜잭션이 롤백될 때의 `afterCompletion`.
     *
     *  - **지운 키를 남기면** 두 번째 호출이 같은 키를 또 지운다. 삭제 자체는 무해하지만
     *    "지웠다" 로그가 두 배로 남아 고아를 추적할 때 어느 것이 실제인지 알 수 없게 된다.
     *  - **못 지운 키를 빼면** 두 번째 기회가 사라진다. 스토리지가 잠깐 흔들려 실패한
     *    경우까지 영구 고아가 되는데, 롤백 보상은 이미 등록돼 있으므로 그냥 다시 시도하면
     *    되는 상황이다. 예전에는 목록을 통째로 비워서 이쪽이었다.
     *
     * 그래서 결과에 따라 나눈다. 지운 것은 빼고, 못 지운 것만 남겨 다음 보상이 집는다.
     *
     * 재시도까지 실패하면 그때는 목록에 남아 있어도 부를 사람이 없다 — 매 시도마다 남기는
     * error 로그가 사본을 찾을 단서다. 실패를 성공으로 가장하지 않는다.
     */
    private fun discardCopiedObjects(videoId: Long, copiedKeys: MutableList<String>) {
        if (copiedKeys.isEmpty()) return
        val keys = copiedKeys.toList()
        log.warn("에셋 승격 실패 — 복사한 객체 {}건을 되돌린다. videoId={}", keys.size, videoId)

        val unresolved = keys.filter { key ->
            runCatching { fileStoragePort.deleteByKey(key) }
                .onFailure { log.error("승격 사본 정리 실패 — 다음 보상에서 다시 시도한다. videoId={} key={}", videoId, key, it) }
                .isFailure
        }

        copiedKeys.clear()
        copiedKeys += unresolved
    }

    private companion object {
        const val VIDEO_FILE_TYPE = "VIDEO"
        const val MAX_TITLE_LENGTH = 100
        const val MAX_OBJECT_NAME_LENGTH = 180
        const val DEFAULT_TITLE = "라이브러리 영상"
        const val DEFAULT_OBJECT_NAME = "asset.bin"
    }
}
