# 멀티 워크스페이스 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 사용자가 여러 워크스페이스(브랜드/채널 단위)를 생성하고 전환하며, 팀 멤버를 워크스페이스 단위로 초대·관리할 수 있게 한다.

**Architecture:** `workspaces` 테이블을 생성하고 기존 `team_members`에 `workspace_id`를 추가. 기존 사용자에게는 자동으로 기본 워크스페이스를 생성하여 하위 호환성 유지. API 레벨에서 `X-Workspace-Id` 헤더 또는 쿼리 파라미터로 워크스페이스 컨텍스트를 전달. 프론트엔드 사이드바에 워크스페이스 전환 드롭다운 추가.

**Tech Stack:** Spring Boot 4 + Kotlin + jOOQ, Vue 3 + Pinia + Tailwind CSS

---

### Task 1: DB 마이그레이션 — workspaces 테이블 + team_members 확장

**Files:**
- Create: `backend/sql/V14__workspaces.sql`

**Step 1: 마이그레이션 SQL 작성**

```sql
-- V14__workspaces.sql

-- 워크스페이스 테이블
CREATE TABLE workspaces (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    logo_url    VARCHAR(500),
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_workspaces_slug ON workspaces(slug);
CREATE INDEX idx_workspaces_owner ON workspaces(owner_id);

-- 워크스페이스 멤버 테이블 (기존 team_members를 대체하지 않고 workspace 연결 추가)
ALTER TABLE team_members ADD COLUMN IF NOT EXISTS workspace_id BIGINT REFERENCES workspaces(id);
CREATE INDEX idx_team_members_workspace ON team_members(workspace_id);

-- 기존 사용자별 기본 워크스페이스 생성
INSERT INTO workspaces (owner_id, name, slug)
SELECT id, COALESCE(nickname, name, email), CONCAT('ws-', id)
FROM users
ON CONFLICT DO NOTHING;

-- 기존 team_members에 workspace_id 설정 (사용자의 기본 워크스페이스 연결)
UPDATE team_members tm
SET workspace_id = w.id
FROM workspaces w
WHERE tm.user_id = w.owner_id
  AND tm.workspace_id IS NULL;
```

**Step 2: Commit**

```bash
git add backend/sql/V14__workspaces.sql
git commit -m "chore: 워크스페이스 DB 마이그레이션 — workspaces 테이블, team_members 확장"
```

---

### Task 2: 도메인 엔티티 + 리포지토리

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/workspace/Workspace.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/workspace/WorkspaceRepository.kt`
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/team/TeamMember.kt`

**Step 1: Workspace 엔티티**

```kotlin
package com.ongo.domain.workspace

import java.time.LocalDateTime

data class Workspace(
    val id: Long? = null,
    val ownerId: Long,
    val name: String,
    val slug: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
```

**Step 2: WorkspaceRepository 인터페이스**

```kotlin
package com.ongo.domain.workspace

interface WorkspaceRepository {
    fun findById(id: Long): Workspace?
    fun findBySlug(slug: String): Workspace?
    fun findByOwnerId(ownerId: Long): List<Workspace>
    fun findAccessibleByUserId(userId: Long): List<Workspace>
    fun save(workspace: Workspace): Workspace
    fun update(workspace: Workspace): Workspace
    fun delete(id: Long)
    fun countByOwnerId(ownerId: Long): Int
}
```

`findAccessibleByUserId` — 사용자가 소유하거나 멤버로 초대된(JOINED) 모든 워크스페이스 조회.

**Step 3: TeamMember에 workspaceId 추가**

기존 `TeamMember` data class에 필드 추가:
```kotlin
val workspaceId: Long? = null,
```

**Step 4: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: FAIL (jOOQ 구현 미완료 — 다음 Task에서 해결)

**Step 5: Commit**

```bash
git add backend/onGo-domain/
git commit -m "feat: 워크스페이스 도메인 — Workspace 엔티티, 리포지토리 인터페이스"
```

---

### Task 3: jOOQ 인프라 — Tables/Fields + Repository 구현

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/WorkspaceJooqRepository.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/TeamMemberJooqRepository.kt`

**Step 1: Tables.kt에 테이블/필드 등록**

Tables 객체에:
```kotlin
val WORKSPACES = DSL.table("workspaces")
```

Fields 객체에:
```kotlin
// workspaces
val OWNER_ID = DSL.field("owner_id", Long::class.java)
val WORKSPACE_NAME = DSL.field("name", String::class.java)
val WORKSPACE_SLUG = DSL.field("slug", String::class.java)
val WORKSPACE_DESCRIPTION = DSL.field("description", String::class.java)
val WORKSPACE_LOGO_URL = DSL.field("logo_url", String::class.java)

// team_members extended
val WORKSPACE_ID = DSL.field("workspace_id", Long::class.java)
```

**Step 2: WorkspaceJooqRepository 구현**

```kotlin
package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.infrastructure.persistence.jooq.Fields.*
import com.ongo.infrastructure.persistence.jooq.Tables.*
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class WorkspaceJooqRepository(
    private val dsl: DSLContext,
) : WorkspaceRepository {

    override fun findById(id: Long): Workspace? =
        dsl.select().from(WORKSPACES).where(ID.eq(id)).fetchOne()?.toWorkspace()

    override fun findBySlug(slug: String): Workspace? =
        dsl.select().from(WORKSPACES).where(WORKSPACE_SLUG.eq(slug)).fetchOne()?.toWorkspace()

    override fun findByOwnerId(ownerId: Long): List<Workspace> =
        dsl.select().from(WORKSPACES).where(OWNER_ID.eq(ownerId))
            .orderBy(CREATED_AT.asc()).fetch().map { it.toWorkspace() }

    override fun findAccessibleByUserId(userId: Long): List<Workspace> {
        // 소유한 워크스페이스 + 멤버로 참여한 워크스페이스 (JOINED 상태)
        val userEmail = dsl.select(EMAIL).from(Tables.USERS).where(ID.eq(userId)).fetchOne()?.get(EMAIL) ?: return emptyList()

        return dsl.selectDistinct(
            DSL.field("w.id", Long::class.java),
            DSL.field("w.owner_id", Long::class.java),
            DSL.field("w.name", String::class.java),
            DSL.field("w.slug", String::class.java),
            DSL.field("w.description", String::class.java),
            DSL.field("w.logo_url", String::class.java),
            DSL.field("w.created_at", java.time.LocalDateTime::class.java),
            DSL.field("w.updated_at", java.time.LocalDateTime::class.java),
        )
        .from(DSL.table("workspaces").`as`("w"))
        .leftJoin(DSL.table("team_members").`as`("tm"))
        .on(DSL.field("tm.workspace_id", Long::class.java).eq(DSL.field("w.id", Long::class.java)))
        .where(
            DSL.field("w.owner_id", Long::class.java).eq(userId)
            .or(
                DSL.field("tm.member_email", String::class.java).eq(userEmail)
                .and(DSL.field("tm.status", String::class.java).eq("JOINED"))
            )
        )
        .orderBy(DSL.field("w.created_at").asc())
        .fetch()
        .map { record ->
            Workspace(
                id = record.get("id", Long::class.java),
                ownerId = record.get("owner_id", Long::class.java),
                name = record.get("name", String::class.java),
                slug = record.get("slug", String::class.java),
                description = record.get("description", String::class.java),
                logoUrl = record.get("logo_url", String::class.java),
                createdAt = record.get("created_at", java.time.LocalDateTime::class.java),
                updatedAt = record.get("updated_at", java.time.LocalDateTime::class.java),
            )
        }
    }

    override fun save(workspace: Workspace): Workspace {
        val id = dsl.insertInto(WORKSPACES)
            .set(OWNER_ID, workspace.ownerId)
            .set(WORKSPACE_NAME, workspace.name)
            .set(WORKSPACE_SLUG, workspace.slug)
            .set(WORKSPACE_DESCRIPTION, workspace.description)
            .set(WORKSPACE_LOGO_URL, workspace.logoUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun update(workspace: Workspace): Workspace {
        dsl.update(WORKSPACES)
            .set(WORKSPACE_NAME, workspace.name)
            .set(WORKSPACE_SLUG, workspace.slug)
            .set(WORKSPACE_DESCRIPTION, workspace.description)
            .set(WORKSPACE_LOGO_URL, workspace.logoUrl)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(workspace.id))
            .execute()
        return findById(workspace.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(WORKSPACES).where(ID.eq(id)).execute()
    }

    override fun countByOwnerId(ownerId: Long): Int =
        dsl.selectCount().from(WORKSPACES).where(OWNER_ID.eq(ownerId)).fetchOne(0, Int::class.java)!!

    private fun Record.toWorkspace(): Workspace = Workspace(
        id = get(ID),
        ownerId = get(OWNER_ID),
        name = get(WORKSPACE_NAME),
        slug = get(WORKSPACE_SLUG),
        description = get(WORKSPACE_DESCRIPTION),
        logoUrl = get(WORKSPACE_LOGO_URL),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
```

**Step 3: TeamMemberJooqRepository 수정**

기존 save/update 메서드에 `workspace_id` 필드 추가:
- `save()`: `.set(WORKSPACE_ID, member.workspaceId)` 추가
- `toTeamMember()` 매핑: `workspaceId = get(WORKSPACE_ID)` 추가

**Step 4: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/onGo-infrastructure/
git commit -m "feat: 워크스페이스 jOOQ 인프라 — Repository 구현, TeamMember workspace_id 연동"
```

---

### Task 4: 백엔드 API — WorkspaceUseCase + DTO + Controller

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/workspace/WorkspaceUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/workspace/dto/WorkspaceDtos.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/workspace/WorkspaceController.kt`

**Step 1: DTO 작성**

```kotlin
package com.ongo.application.workspace.dto

import java.time.LocalDateTime

data class WorkspaceResponse(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val slug: String,
    val description: String?,
    val logoUrl: String?,
    val memberCount: Int,
    val createdAt: LocalDateTime?,
)

data class CreateWorkspaceRequest(
    val name: String,
    val slug: String,
    val description: String? = null,
)

data class UpdateWorkspaceRequest(
    val name: String? = null,
    val slug: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
)
```

**Step 2: WorkspaceUseCase 구현**

```kotlin
package com.ongo.application.workspace

import com.ongo.application.workspace.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.DuplicateResourceException
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

    fun listWorkspaces(userId: Long): List<WorkspaceResponse> {
        return workspaceRepository.findAccessibleByUserId(userId).map { it.toResponse() }
    }

    fun getWorkspace(userId: Long, workspaceId: Long): WorkspaceResponse {
        val workspace = workspaceRepository.findById(workspaceId)
            ?: throw NotFoundException("워크스페이스", workspaceId)
        return workspace.toResponse()
    }

    @Transactional
    fun createWorkspace(userId: Long, request: CreateWorkspaceRequest): WorkspaceResponse {
        // 플랜별 워크스페이스 제한 확인
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
            throw BusinessException("현재 플랜에서 최대 ${maxWorkspaces}개의 워크스페이스를 생성할 수 있습니다. 업그레이드해주세요.")
        }

        // slug 중복 검사
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

        // 기본 워크스페이스(첫 번째)는 삭제 불가
        val allWorkspaces = workspaceRepository.findByOwnerId(userId)
        if (allWorkspaces.size <= 1) {
            throw BusinessException("기본 워크스페이스는 삭제할 수 없습니다")
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
        val memberCount = if (id != null) teamMemberRepository.countByUserId(id) else 0
        return WorkspaceResponse(
            id = id!!,
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
```

**Step 3: Controller 구현**

```kotlin
package com.ongo.api.workspace

import com.ongo.api.config.CurrentUser
import com.ongo.application.workspace.WorkspaceUseCase
import com.ongo.application.workspace.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "워크스페이스", description = "워크스페이스 CRUD 및 전환")
@RestController
@RequestMapping("/api/v1/workspaces")
class WorkspaceController(
    private val workspaceUseCase: WorkspaceUseCase,
) {

    @Operation(summary = "워크스페이스 목록 조회")
    @GetMapping
    fun listWorkspaces(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WorkspaceResponse>>> =
        ResData.success(workspaceUseCase.listWorkspaces(userId))

    @Operation(summary = "워크스페이스 상세 조회")
    @GetMapping("/{id}")
    fun getWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkspaceResponse>> =
        ResData.success(workspaceUseCase.getWorkspace(userId, id))

    @Operation(summary = "워크스페이스 생성")
    @PostMapping
    fun createWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWorkspaceRequest,
    ): ResponseEntity<ResData<WorkspaceResponse>> =
        ResData.success(workspaceUseCase.createWorkspace(userId, request))

    @Operation(summary = "워크스페이스 수정")
    @PutMapping("/{id}")
    fun updateWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWorkspaceRequest,
    ): ResponseEntity<ResData<WorkspaceResponse>> =
        ResData.success(workspaceUseCase.updateWorkspace(userId, id, request))

    @Operation(summary = "워크스페이스 삭제")
    @DeleteMapping("/{id}")
    fun deleteWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Void>> {
        workspaceUseCase.deleteWorkspace(userId, id)
        return ResData.success(null)
    }
}
```

**Step 4: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/workspace/ backend/onGo-api/src/main/kotlin/com/ongo/api/workspace/
git commit -m "feat: 워크스페이스 API — CRUD 엔드포인트, 플랜별 제한, 기본 워크스페이스 자동 생성"
```

---

### Task 5: 프론트엔드 — 타입 + API + 스토어

**Files:**
- Create: `frontend/src/types/workspace.ts`
- Create: `frontend/src/api/workspace.ts`
- Create: `frontend/src/stores/workspace.ts`

**Step 1: 타입 정의**

```typescript
// frontend/src/types/workspace.ts
export interface Workspace {
  id: number
  ownerId: number
  name: string
  slug: string
  description: string | null
  logoUrl: string | null
  memberCount: number
  createdAt: string | null
}

export interface CreateWorkspaceRequest {
  name: string
  slug: string
  description?: string
}

export interface UpdateWorkspaceRequest {
  name?: string
  slug?: string
  description?: string
  logoUrl?: string
}
```

**Step 2: API 클라이언트**

```typescript
// frontend/src/api/workspace.ts
import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest } from '@/types/workspace'

export const workspaceApi = {
  list() {
    return apiClient.get<ResData<Workspace[]>>('/workspaces').then(unwrapResponse)
  },
  get(id: number) {
    return apiClient.get<ResData<Workspace>>(`/workspaces/${id}`).then(unwrapResponse)
  },
  create(request: CreateWorkspaceRequest) {
    return apiClient.post<ResData<Workspace>>('/workspaces', request).then(unwrapResponse)
  },
  update(id: number, request: UpdateWorkspaceRequest) {
    return apiClient.put<ResData<Workspace>>(`/workspaces/${id}`, request).then(unwrapResponse)
  },
  remove(id: number) {
    return apiClient.delete<ResData<void>>(`/workspaces/${id}`).then(unwrapResponse)
  },
}
```

**Step 3: 스토어**

```typescript
// frontend/src/stores/workspace.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest } from '@/types/workspace'
import { workspaceApi } from '@/api/workspace'

const STORAGE_KEY = 'ongo_active_workspace_id'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([])
  const activeWorkspaceId = ref<number | null>(null)
  const loading = ref(false)

  const activeWorkspace = computed(() =>
    workspaces.value.find(w => w.id === activeWorkspaceId.value) ?? workspaces.value[0] ?? null
  )

  async function fetchWorkspaces() {
    loading.value = true
    try {
      workspaces.value = await workspaceApi.list()
      // 저장된 활성 워크스페이스 복원 또는 첫 번째 워크스페이스 설정
      const savedId = localStorage.getItem(STORAGE_KEY)
      if (savedId && workspaces.value.some(w => w.id === Number(savedId))) {
        activeWorkspaceId.value = Number(savedId)
      } else if (workspaces.value.length > 0) {
        activeWorkspaceId.value = workspaces.value[0].id
      }
    } catch {
      workspaces.value = []
    } finally {
      loading.value = false
    }
  }

  function switchWorkspace(id: number) {
    activeWorkspaceId.value = id
    localStorage.setItem(STORAGE_KEY, String(id))
  }

  async function createWorkspace(request: CreateWorkspaceRequest) {
    const workspace = await workspaceApi.create(request)
    workspaces.value.push(workspace)
    return workspace
  }

  async function updateWorkspace(id: number, request: UpdateWorkspaceRequest) {
    const workspace = await workspaceApi.update(id, request)
    const idx = workspaces.value.findIndex(w => w.id === id)
    if (idx !== -1) workspaces.value[idx] = workspace
    return workspace
  }

  async function removeWorkspace(id: number) {
    await workspaceApi.remove(id)
    workspaces.value = workspaces.value.filter(w => w.id !== id)
    if (activeWorkspaceId.value === id && workspaces.value.length > 0) {
      switchWorkspace(workspaces.value[0].id)
    }
  }

  return {
    workspaces,
    activeWorkspaceId,
    activeWorkspace,
    loading,
    fetchWorkspaces,
    switchWorkspace,
    createWorkspace,
    updateWorkspace,
    removeWorkspace,
  }
})
```

**Step 4: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/types/workspace.ts frontend/src/api/workspace.ts frontend/src/stores/workspace.ts
git commit -m "feat: 워크스페이스 프론트엔드 타입/API/스토어"
```

---

### Task 6: 사이드바 워크스페이스 전환 드롭다운

**Files:**
- Create: `frontend/src/components/layout/WorkspaceSwitcher.vue`
- Modify: `frontend/src/components/layout/SideNav.vue`

**Step 1: WorkspaceSwitcher 컴포넌트**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ChevronUpDownIcon, PlusIcon, CheckIcon } from '@heroicons/vue/24/outline'
import { useWorkspaceStore } from '@/stores/workspace'

const props = defineProps<{ collapsed: boolean }>()

const workspaceStore = useWorkspaceStore()
const isOpen = ref(false)

onMounted(() => {
  if (workspaceStore.workspaces.length === 0) {
    workspaceStore.fetchWorkspaces()
  }
})

function selectWorkspace(id: number) {
  workspaceStore.switchWorkspace(id)
  isOpen.value = false
}

function getInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}
</script>

<template>
  <div class="relative px-2 py-2">
    <button
      @click="isOpen = !isOpen"
      class="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-gray-100 dark:hover:bg-gray-800"
    >
      <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30 text-sm font-bold text-primary-700 dark:text-primary-400">
        {{ workspaceStore.activeWorkspace ? getInitial(workspaceStore.activeWorkspace.name) : '?' }}
      </div>
      <template v-if="!props.collapsed">
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ workspaceStore.activeWorkspace?.name ?? '워크스페이스' }}
          </p>
          <p class="truncate text-xs text-gray-500 dark:text-gray-400">
            {{ workspaceStore.activeWorkspace?.memberCount ?? 0 }}명
          </p>
        </div>
        <ChevronUpDownIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
      </template>
    </button>

    <!-- Dropdown -->
    <div
      v-if="isOpen"
      class="absolute left-2 right-2 top-full z-50 mt-1 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 py-1 shadow-lg"
    >
      <button
        v-for="ws in workspaceStore.workspaces"
        :key="ws.id"
        @click="selectWorkspace(ws.id)"
        class="flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
      >
        <div class="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded bg-gray-100 dark:bg-gray-700 text-xs font-bold text-gray-600 dark:text-gray-300">
          {{ getInitial(ws.name) }}
        </div>
        <span class="flex-1 truncate text-gray-700 dark:text-gray-300">{{ ws.name }}</span>
        <CheckIcon v-if="ws.id === workspaceStore.activeWorkspaceId" class="h-4 w-4 text-primary-600" />
      </button>
      <div class="border-t border-gray-100 dark:border-gray-700 mt-1 pt-1">
        <router-link
          to="/settings?tab=workspaces"
          @click="isOpen = false"
          class="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700"
        >
          <PlusIcon class="h-4 w-4" />
          워크스페이스 관리
        </router-link>
      </div>
    </div>
  </div>
</template>
```

**Step 2: SideNav.vue에 WorkspaceSwitcher 삽입**

Logo 섹션 바로 아래에 `<WorkspaceSwitcher :collapsed="collapsed" />` 추가.

```typescript
import WorkspaceSwitcher from '@/components/layout/WorkspaceSwitcher.vue'
```

**Step 3: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 4: Commit**

```bash
git add frontend/src/components/layout/WorkspaceSwitcher.vue frontend/src/components/layout/SideNav.vue
git commit -m "feat: 사이드바 워크스페이스 전환 드롭다운 추가"
```

---

### Task 7: 전체 빌드 검증

**Step 1: 백엔드 빌드**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: PASS

**Step 2: 프론트엔드 타입 체크**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 3: Vite 프로덕션 빌드**

```bash
cd frontend && npx vite build 2>&1 | tail -10
```

Expected: 빌드 성공
