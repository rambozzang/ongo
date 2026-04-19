export interface TagTemplate {
  id: number
  name: string
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface TagTemplateCreateRequest {
  name: string
  tags: string[]
}

export interface TagTemplateUpdateRequest {
  name: string
  tags: string[]
}
