package com.ongo.domain.asset

interface AssetRepository {
    fun findById(id: Long): Asset?
    /**
     * 조건에 맞는 한 페이지. 최신순이다.
     *
     * 조건은 [count] 와 **같은 값**을 받아야 한다. 목록과 총계가 다른 조건을 쓰면
     * 페이지네이션이 존재하지 않는 페이지를 가리킨다.
     */
    fun findByUserId(userId: Long, query: AssetQuery, page: Int, size: Int): List<Asset>

    /** [findByUserId] 와 **같은 조건**으로 센 총 개수. */
    fun count(userId: Long, query: AssetQuery): Int
    fun save(asset: Asset): Asset
    fun update(asset: Asset): Asset
    fun delete(id: Long)
}
