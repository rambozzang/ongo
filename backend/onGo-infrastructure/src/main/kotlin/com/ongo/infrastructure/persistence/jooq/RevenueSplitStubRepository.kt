package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenuesplit.*
import org.springframework.stereotype.Repository

@Repository
class RevenueSplitStubRepository : RevenueSplitRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<RevenueSplit> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RevenueSplit> = emptyList()
    override fun findById(id: Long): RevenueSplit? = null
    override fun save(split: RevenueSplit): RevenueSplit = split.copy(id = 1)
    override fun update(split: RevenueSplit): RevenueSplit = split
    override fun deleteById(id: Long) {}
}

@Repository
class SplitMemberStubRepository : SplitMemberRepository {
    override fun findBySplitId(splitId: Long): List<SplitMember> = emptyList()
    override fun findById(id: Long): SplitMember? = null
    override fun save(member: SplitMember): SplitMember = member.copy(id = 1)
    override fun saveBatch(members: List<SplitMember>): List<SplitMember> = members
    override fun update(member: SplitMember): SplitMember = member
    override fun deleteBySplitId(splitId: Long) {}
}
