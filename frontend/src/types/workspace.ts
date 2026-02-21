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
