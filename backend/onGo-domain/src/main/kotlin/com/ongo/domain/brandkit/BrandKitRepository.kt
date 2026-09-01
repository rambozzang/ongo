package com.ongo.domain.brandkit

interface BrandKitRepository {
    fun findById(id: Long): BrandKit?
    fun findByUserId(userId: Long): List<BrandKit>
    fun save(brandKit: BrandKit): BrandKit
    fun update(brandKit: BrandKit): BrandKit
    fun delete(id: Long)
    fun clearDefault(userId: Long)

    /**
     * 이 에셋을 쓰고 있는 **내 브랜드킷 이름들**.
     *
     * 에셋을 지우면 그것을 가리키던 브랜드킷의 로고·워터마크가 조용히 깨진다. 지우기
     * 전에 물어보기 위한 조회다. 이름을 돌려주는 이유는 사용자가 **어느 브랜드킷을
     * 고쳐야 하는지** 알아야 하기 때문이다 — 건수만으로는 찾아갈 수 없다.
     *
     * `assetId` 로만 찾는다. URL 문자열로 맞춰 보지 않는다 — 그 값은 서명이 붙어 있고
     * 조회할 때마다 달라져서 같은 파일이어도 일치하지 않는다.
     *
     * 다른 사용자의 브랜드킷은 보지 않는다. 남의 참조 때문에 내 에셋을 못 지우면
     * 그 자체가 남의 데이터를 알려 주는 통로가 된다.
     */
    fun findNamesReferencingAsset(userId: Long, assetId: Long): List<String>
}
