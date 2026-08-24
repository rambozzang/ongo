package com.ongo.application.ai.audio

import org.springframework.core.io.Resource

/**
 * 전사에 넘길 오디오를 준비하는 경계.
 *
 * ## 왜 포트로 분리하는가
 *
 * 이전 구현은 원본 **영상** URL 을 그대로 전사 모델에 넘겼다. 영상 트랙까지 통째로
 * 올리는 데다 길이 제한도 없어서, 실제 롱폼 원본에서는 요청이 거부되거나 API 서버가
 * 파일 전체를 중계하며 대역폭과 힙을 함께 썼다.
 *
 * 오디오 추출·분할은 ffmpeg 프로세스를 띄우는 일이라 인프라의 관심사다. 여기서 포트로
 * 끊어두면 유스케이스 테스트가 외부 바이너리 없이 병합·실패 처리만 검증할 수 있다.
 */
interface TranscriptionAudioPort {

    /**
     * 인코더를 지금 쓸 수 있는지. false 면 전사 모델을 **호출하지 않고** 실패시킨다 —
     * 어차피 준비가 안 되므로, 크레딧과 시간을 쓴 뒤 실패하는 것보다 낫다.
     */
    fun isAvailable(): Boolean

    /**
     * 원본의 재생 길이를 **측정**한다. 추정하지 않는다.
     *
     * 바이트 크기로는 길이를 알 수 없다 — 저화질 롱폼은 작고 길다. 크기 상한만 두면
     * 3시간짜리 200MB 원본이 통과해 조각 수·전사 비용·큐 대기가 상한 없이 늘어난다.
     *
     * 실패는 반드시 예외다. 0 이나 null 을 돌려주면 호출자가 "짧은 영상"으로 오인해
     * 통과시키고, 그 결과는 돈이 걸린 파이프라인이 한참 뒤에 죽는 것이다.
     *
     * @return 밀리초 단위 재생 길이. 항상 0 보다 크다.
     * @throws AudioPreparationException 프로브를 못 쓰거나 출력이 길이로 읽히지 않을 때.
     */
    fun probeDurationMs(sourceUrl: String): Long

    /**
     * 원본에서 오디오만 뽑아 설정된 상한 이하의 조각으로 나눈다.
     *
     * 반환값은 반드시 [PreparedAudio.close] 로 정리한다. 조각은 로컬 임시 파일이고
     * 원본만큼 오래 남으면 디스크가 찬다.
     *
     * @throws AudioPreparationException 추출 실패. 이때 조각은 이미 정리된 상태다.
     */
    fun prepare(sourceUrl: String): PreparedAudio
}

/**
 * 준비된 오디오 조각들. [parts] 는 재생 순서이며 각 조각은 원본 기준 시작 오프셋을 갖는다.
 */
interface PreparedAudio : AutoCloseable {
    val parts: List<AudioPart>
}

/**
 * 오디오 한 조각.
 *
 * @param offsetMs 원본에서 이 조각이 시작하는 시각. 조각별 전사 결과의 타임스탬프는
 *   조각 기준(0부터)이라 이 값을 더해야 원본 타임라인으로 돌아온다. 추정값이 아니라
 *   인코더가 실제로 자른 지점이어야 한다.
 */
data class AudioPart(
    val resource: Resource,
    val offsetMs: Long,
)

/** 오디오 준비 실패. 사용자에게 보일 문구는 유스케이스가 정한다. */
class AudioPreparationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
