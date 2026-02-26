package com.ongo.domain.revenuesplit

interface SplitMemberRepository {
    fun findBySplitId(splitId: Long): List<SplitMember>
    fun findById(id: Long): SplitMember?
    fun save(member: SplitMember): SplitMember
    fun saveBatch(members: List<SplitMember>): List<SplitMember>
    fun update(member: SplitMember): SplitMember
    fun deleteBySplitId(splitId: Long)
}
