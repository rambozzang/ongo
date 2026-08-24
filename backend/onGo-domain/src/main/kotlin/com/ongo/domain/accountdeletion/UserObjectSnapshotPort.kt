package com.ongo.domain.accountdeletion

/**
 * 탈퇴 대상 사용자가 소유한 **우리 버킷의 정확한 객체 키**를 모은다.
 *
 * 세 가지를 반드시 지킨다.
 *
 * 1. **저장된 키만 쓴다.** URL 을 파싱해 키를 추측하지 않는다. `file_url` 은 서명이 붙은
 *    presigned URL 이고 경로 형식도 어댑터마다 달라서, 추측이 빗나가면 남의 파일을 지운다.
 *    되돌릴 수 없는 작업에 추측을 섞지 않는다.
 * 2. **외부 URL 은 대상이 아니다.** 우리가 올린 적 없는 객체는 우리 것이 아니다.
 *    키 컬럼이 비어 있으면 그건 곧 "우리 버킷 객체라는 증거가 없다"는 뜻이다.
 * 3. **다른 살아있는 사용자가 같은 키를 가리키면 제외한다.** 반복 예약은 객체를 복제해
 *    쓰므로 같은 키를 두 행이 가리킬 수 있다. 탈퇴자 것만 보고 지우면 남은 사용자의
 *    영상이 사라진다.
 */
interface UserObjectSnapshotPort {
    /**
     * @param userId 탈퇴 대상
     * @return 지워도 되는 정확한 키 목록과, 키를 확정할 수 없어 사람이 봐야 하는 행 수
     */
    fun snapshot(userId: Long): UserObjectSnapshot
}

/**
 * @param deletableKeys 이 사용자만 참조하는, 우리 버킷의 정확한 키
 * @param unresolvedRowCount 파일은 있는데 키가 없어 판단할 수 없는 행 수.
 *   0 이 아니면 자동 완료를 막는다 — 실제로는 남아 있는데 "다 지웠다"고 표시하는 것이
 *   개인정보 관점에서 가장 나쁜 결과다.
 * @param sharedKeyCount 다른 살아있는 사용자도 참조해 제외한 키 수. 진단용이다.
 */
data class UserObjectSnapshot(
    val deletableKeys: List<String>,
    val unresolvedRowCount: Int,
    val sharedKeyCount: Int,
)
