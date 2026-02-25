package com.ongo.application.agency

import com.ongo.application.agency.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.agency.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AgencyUseCase(
    private val agencyRepository: AgencyRepository,
) {

    fun listWorkspaces(userId: Long): List<AgencyWorkspaceResponse> {
        return agencyRepository.findWorkspacesByOwner(userId).map { ws ->
            val creators = agencyRepository.findCreatorsByWorkspaceId(ws.id!!)
            ws.toResponse(creators)
        }
    }

    fun getWorkspace(userId: Long, workspaceId: Long): AgencyWorkspaceResponse {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        val creators = agencyRepository.findCreatorsByWorkspaceId(workspaceId)
        return workspace.toResponse(creators)
    }

    @Transactional
    fun createWorkspace(userId: Long, request: CreateWorkspaceRequest): AgencyWorkspaceResponse {
        val workspace = AgencyWorkspace(
            ownerUserId = userId,
            name = request.name,
            description = request.description,
            logoUrl = request.logoUrl,
        )
        val saved = agencyRepository.saveWorkspace(workspace)
        return saved.toResponse(emptyList())
    }

    @Transactional
    fun updateWorkspace(userId: Long, workspaceId: Long, request: UpdateWorkspaceRequest): AgencyWorkspaceResponse {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        val updated = workspace.copy(
            name = request.name ?: workspace.name,
            description = request.description ?: workspace.description,
            logoUrl = request.logoUrl ?: workspace.logoUrl,
        )
        val saved = agencyRepository.updateWorkspace(updated)
        val creators = agencyRepository.findCreatorsByWorkspaceId(workspaceId)
        return saved.toResponse(creators)
    }

    @Transactional
    fun deleteWorkspace(userId: Long, workspaceId: Long) {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        agencyRepository.deleteWorkspace(workspaceId)
    }

    @Transactional
    fun addCreator(userId: Long, workspaceId: Long, request: AddCreatorRequest): AgencyCreatorResponse {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        val creator = AgencyCreator(
            workspaceId = workspaceId,
            userId = request.userId,
            role = request.role,
        )
        return agencyRepository.saveCreator(creator).toResponse()
    }

    @Transactional
    fun removeCreator(userId: Long, workspaceId: Long, creatorId: Long) {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        agencyRepository.deleteCreator(creatorId)
    }

    fun listPortals(userId: Long, workspaceId: Long): List<ClientPortalResponse> {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        return agencyRepository.findPortalsByWorkspaceId(workspaceId).map { it.toResponse() }
    }

    @Transactional
    fun createPortal(userId: Long, workspaceId: Long, request: CreatePortalRequest): ClientPortalResponse {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        val portal = ClientPortal(
            workspaceId = workspaceId,
            clientName = request.clientName,
            accessToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
            permissions = request.permissions,
            expiresAt = request.expiresAt,
        )
        return agencyRepository.savePortal(portal).toResponse()
    }

    @Transactional
    fun deletePortal(userId: Long, workspaceId: Long, portalId: Long) {
        val workspace = agencyRepository.findWorkspaceById(workspaceId) ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerUserId != userId) throw ForbiddenException("해당 워크스페이스에 대한 권한이 없습니다")
        agencyRepository.deletePortal(portalId)
    }

    private fun AgencyWorkspace.toResponse(creators: List<AgencyCreator>) = AgencyWorkspaceResponse(
        id = id!!,
        ownerUserId = ownerUserId,
        name = name,
        description = description,
        logoUrl = logoUrl,
        creators = creators.map { it.toResponse() },
        createdAt = createdAt,
    )

    private fun AgencyCreator.toResponse() = AgencyCreatorResponse(
        id = id!!,
        userId = userId,
        role = role,
        joinedAt = joinedAt,
    )

    private fun ClientPortal.toResponse() = ClientPortalResponse(
        id = id!!,
        workspaceId = workspaceId,
        clientName = clientName,
        accessToken = accessToken,
        permissions = permissions,
        expiresAt = expiresAt,
        createdAt = createdAt,
    )
}
