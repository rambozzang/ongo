package com.ongo.infrastructure.storage

import com.ongo.application.common.FileStoragePort
import com.ongo.infrastructure.external.storage.StorageClient
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class FileStoragePortAdapter(
    private val storageClient: StorageClient,
) : FileStoragePort {

    override fun uploadByKey(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        return storageClient.uploadFile(key, inputStream, contentType, size)
    }

    override fun deleteByKey(key: String) {
        storageClient.deleteFile(key)
    }

    /**
     * 업로드가 돌려준 것과 **같은 종류의 URL을 다시** 만든다.
     *
     * [StorageClient.uploadFile]의 반환값이 곧 `getFileUrl(key)`이므로, 같은 메서드로
     * 다시 발급하면 환경별 URL 형식이 그대로 유지된다. 바뀌는 것은 유효기간뿐이다.
     *
     *  - **운영(S3/R2)** — `getFileUrl`이 7일짜리 presigned GET을 새로 서명한다. 저장된
     *    값을 재사용하지 않으므로 만료 시계가 조회 시점부터 다시 간다. 이것이 이 메서드를
     *    만든 이유다.
     *  - **로컬(MinIO)** — 엔드포인트 기반 정적 URL이라 애초에 만료가 없다. 여기서도 같은
     *    값을 돌려주므로 개발 환경 동작은 달라지지 않는다.
     *
     * 두 경우 모두 **실제 어댑터 구현**을 그대로 탄다. 목이나 조립한 URL을 만들지 않는다 —
     * 그런 값은 눌러 보기 전까지 진짜와 구분되지 않는다.
     */
    override fun downloadUrlByKey(key: String): String {
        return storageClient.getFileUrl(key)
    }

    /**
     * 복사도 URL 발급도 **어댑터가 한다.** 여기서 하는 일은 순서를 정하는 것뿐이다.
     *
     * 복사가 실패하면 그대로 던진다 — 사본이 없는데 URL을 만들어 주면 호출부는 성공으로
     * 읽고, 깨진 링크를 가진 행이 저장된다. 키도 손대지 않는다. 정규화나 접두사 보정을
     * 끼워 넣으면 의도와 다른 오브젝트를 복사하거나 가리키게 된다.
     */
    override fun copyByKey(sourceKey: String, targetKey: String): String {
        storageClient.copyObject(sourceKey, targetKey)
        return storageClient.getFileUrl(targetKey)
    }
}
