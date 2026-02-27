package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.sponsorship.*
import org.springframework.stereotype.Repository

@Repository
class SponsorshipStubRepository : SponsorshipRepository {
    override fun findByUserId(userId: Long): List<Sponsorship> = emptyList()
    override fun findByUserIdAndStatus(userId: Long, status: String): List<Sponsorship> = emptyList()
    override fun findById(id: Long): Sponsorship? = null
    override fun save(sponsorship: Sponsorship): Sponsorship = sponsorship.copy(id = 1)
    override fun update(sponsorship: Sponsorship): Sponsorship = sponsorship
    override fun deleteById(id: Long) {}
}

@Repository
class SponsorshipDeliverableStubRepository : SponsorshipDeliverableRepository {
    override fun findBySponsorshipId(sponsorshipId: Long): List<SponsorshipDeliverable> = emptyList()
    override fun findById(id: Long): SponsorshipDeliverable? = null
    override fun save(deliverable: SponsorshipDeliverable): SponsorshipDeliverable = deliverable.copy(id = 1)
    override fun update(deliverable: SponsorshipDeliverable): SponsorshipDeliverable = deliverable
    override fun deleteById(id: Long) {}
}
