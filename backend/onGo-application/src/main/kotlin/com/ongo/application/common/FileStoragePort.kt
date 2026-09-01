package com.ongo.application.common

import java.io.InputStream

interface FileStoragePort {
    fun uploadByKey(key: String, inputStream: InputStream, contentType: String, size: Long): String
    fun deleteByKey(key: String)

    /**
     * 저장된 키로 **지금 유효한** 다운로드 URL을 새로 발급한다.
     *
     * 업로드 시점에 받은 URL을 DB에 적어 두고 계속 돌려주면 안 된다. S3/R2 어댑터가
     * 돌려주는 값은 서명이 붙은 7일짜리 URL이라, 8일째부터 그 링크는 403이 된다. 파일은
     * 멀쩡히 있는데 화면만 깨지므로 사용자에게는 "파일이 사라졌다"로 보인다.
     *
     * 그래서 **키**를 근거로 매 조회마다 다시 발급한다. 키는 서버가 할당해 저장해 둔 값이라
     * 추측이 섞이지 않는다 — URL에서 경로를 되짚는 방식은 서명·엔드포인트 형식이 어댑터마다
     * 달라 빗나가고, 빗나간 키는 남의 파일을 가리킬 수 있어 쓰지 않는다.
     *
     * 어댑터별 실제 동작은 [com.ongo.infrastructure.storage.FileStoragePortAdapter] 참고.
     */
    fun downloadUrlByKey(key: String): String

    /**
     * 오브젝트를 **복사**하고 사본의 다운로드 URL을 돌려준다.
     *
     * 원본을 두 곳에서 가리키게 하지 않는다. 키를 공유하면 한쪽을 지우는 순간 다른 쪽이
     * 깨지는데, 그 사실은 지우는 사람에게 보이지 않는다 — 에셋을 정리했더니 이미 게시한
     * 영상이 사라지는 식이다. 복사는 저장공간을 두 배 쓰지만 수명주기를 서로 독립시킨다.
     * 반복 예약([com.ongo.application.video.StorageService.copyVideoFile])도 같은 이유로
     * 복사를 택하고 있다.
     *
     * @return 사본 키로 발급한 URL. 조립하지 않고 어댑터가 만든 값을 그대로 준다.
     */
    fun copyByKey(sourceKey: String, targetKey: String): String
}
