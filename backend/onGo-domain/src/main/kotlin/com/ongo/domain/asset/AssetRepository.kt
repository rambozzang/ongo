package com.ongo.domain.asset

interface AssetRepository {
    fun findById(id: Long): Asset?
    fun findByUserId(userId: Long, page: Int, size: Int): List<Asset>
    fun findByUserIdAndFileType(userId: Long, fileType: String, page: Int, size: Int): List<Asset>
    fun findByUserIdAndFolder(userId: Long, folder: String, page: Int, size: Int): List<Asset>
    fun countByUserId(userId: Long): Int
    fun save(asset: Asset): Asset
    fun update(asset: Asset): Asset
    fun delete(id: Long)
}
