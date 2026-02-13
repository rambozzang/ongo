package com.ongo.domain.team

interface TeamMemberRepository {
    fun findByUserId(userId: Long): List<TeamMember>
    fun findById(id: Long): TeamMember?
    fun findByUserIdAndEmail(userId: Long, email: String): TeamMember?
    fun findByMemberEmailAndUserId(memberEmail: String, teamOwnerId: Long): TeamMember?
    fun save(member: TeamMember): TeamMember
    fun update(member: TeamMember): TeamMember
    fun delete(id: Long)
    fun countByUserId(userId: Long): Int
    fun findTeamsForMember(memberEmail: String): List<TeamMember>
}
