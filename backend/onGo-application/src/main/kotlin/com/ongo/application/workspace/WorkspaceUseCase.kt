package com.ongo.application.workspace

import com.ongo.application.workspace.dto.CreateWorkspaceRequest
import com.ongo.application.workspace.dto.UpdateWorkspaceRequest
import com.ongo.application.workspace.dto.WorkspaceResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val userRepository: UserRepository,
) {

    fun listWorkspaces(userId: Long): List<WorkspaceResponse> =
        workspaceRepository.findAccessibleByUserId(userId).map { it.toResponse() }

    fun getWorkspace(userId: Long, workspaceId: Long): WorkspaceResponse {
        val workspace = workspaceRepository.findById(workspaceId)
            ?: throw NotFoundException("워크스페이스", workspaceId)
        return workspace.toResponse()
    }

    @Transactional
    fun createWorkspace(userId: Long, request: CreateWorkspaceRequest): WorkspaceResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val count = workspaceRepository.countByOwnerId(userId)
        val maxWorkspaces = when (user.planType.name) {
            "FREE" -> 1
            "STARTER" -> 2
            "PRO" -> 5
            "BUSINESS" -> 20
            else -> 1
        }
        if (count >= maxWorkspaces) {
            throw BusinessException("PLAN_LIMIT", "현재 플랜에서 최대 ${maxWorkspaces}개의 워크스페이스를 생성할 수 있습니다. 업그레이드해주세요.")
        }

        if (workspaceRepository.findBySlug(request.slug) != null) {
            throw DuplicateResourceException("워크스페이스 슬러그", request.slug)
        }

        val workspace = Workspace(
            ownerId = userId,
            name = request.name,
            slug = request.slug,
            description = request.description,
        )
        return workspaceRepository.save(workspace).toResponse()
    }

    @Transactional
    fun updateWorkspace(userId: Long, workspaceId: Long, request: UpdateWorkspaceRequest): WorkspaceResponse {
        val workspace = workspaceRepository.findById(workspaceId)
            ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerId != userId) throw ForbiddenException("워크스페이스 수정 권한이 없습니다")

        if (request.slug != null && request.slug != workspace.slug) {
            if (workspaceRepository.findBySlug(request.slug) != null) {
                throw DuplicateResourceException("워크스페이스 슬러그", request.slug)
            }
        }

        val updated = workspace.copy(
            name = request.name ?: workspace.name,
            slug = request.slug ?: workspace.slug,
            description = request.description ?: workspace.description,
            logoUrl = request.logoUrl ?: workspace.logoUrl,
        )
        return workspaceRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteWorkspace(userId: Long, workspaceId: Long) {
        val workspace = workspaceRepository.findById(workspaceId)
            ?: throw NotFoundException("워크스페이스", workspaceId)
        if (workspace.ownerId != userId) throw ForbiddenException("워크스페이스 삭제 권한이 없습니다")

        val allWorkspaces = workspaceRepository.findByOwnerId(userId)
        if (allWorkspaces.size <= 1) {
            throw BusinessException("CANNOT_DELETE", "기본 워크스페이스는 삭제할 수 없습니다")
        }

        workspaceRepository.delete(workspaceId)
    }

    @Transactional
    fun ensureDefaultWorkspace(userId: Long): Workspace {
        val existing = workspaceRepository.findByOwnerId(userId)
        if (existing.isNotEmpty()) return existing.first()

        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val workspace = Workspace(
            ownerId = userId,
            name = user.nickname ?: user.name,
            slug = "ws-${userId}",
        )
        return workspaceRepository.save(workspace)
    }

    private fun Workspace.toResponse(): WorkspaceResponse {
        val wsId = id!!
        val memberCount = teamMemberRepository.countByUserId(wsId)
        return WorkspaceResponse(
            id = wsId,
            ownerId = ownerId,
            name = name,
            slug = slug,
            description = description,
            logoUrl = logoUrl,
            memberCount = memberCount,
            createdAt = createdAt,
        )
    }
}
