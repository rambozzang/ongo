package com.ongo.domain.agency

interface AgencyRepository {
    fun findWorkspaceById(id: Long): AgencyWorkspace?
    fun findWorkspacesByOwner(ownerUserId: Long): List<AgencyWorkspace>
    fun saveWorkspace(workspace: AgencyWorkspace): AgencyWorkspace
    fun updateWorkspace(workspace: AgencyWorkspace): AgencyWorkspace
    fun deleteWorkspace(id: Long)

    fun findCreatorsByWorkspaceId(workspaceId: Long): List<AgencyCreator>
    fun saveCreator(creator: AgencyCreator): AgencyCreator
    fun deleteCreator(id: Long)

    fun findPortalById(id: Long): ClientPortal?
    fun findPortalsByWorkspaceId(workspaceId: Long): List<ClientPortal>
    fun findPortalByAccessToken(token: String): ClientPortal?
    fun savePortal(portal: ClientPortal): ClientPortal
    fun deletePortal(id: Long)
}
