package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.agency.*
import org.springframework.stereotype.Repository

@Repository
class AgencyStubRepository : AgencyRepository {
    override fun findWorkspaceById(id: Long): AgencyWorkspace? = null
    override fun findWorkspacesByOwner(ownerUserId: Long): List<AgencyWorkspace> = emptyList()
    override fun saveWorkspace(workspace: AgencyWorkspace): AgencyWorkspace = workspace.copy(id = 1)
    override fun updateWorkspace(workspace: AgencyWorkspace): AgencyWorkspace = workspace
    override fun deleteWorkspace(id: Long) {}

    override fun findCreatorsByWorkspaceId(workspaceId: Long): List<AgencyCreator> = emptyList()
    override fun saveCreator(creator: AgencyCreator): AgencyCreator = creator.copy(id = 1)
    override fun deleteCreator(id: Long) {}

    override fun findPortalById(id: Long): ClientPortal? = null
    override fun findPortalsByWorkspaceId(workspaceId: Long): List<ClientPortal> = emptyList()
    override fun findPortalByAccessToken(token: String): ClientPortal? = null
    override fun savePortal(portal: ClientPortal): ClientPortal = portal.copy(id = 1)
    override fun deletePortal(id: Long) {}
}
