import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CommunityPost,
  PostComment,
  CommunitySummary,
  CreatePostRequest,
} from '@/types/fanCommunity'

export const fanCommunityApi = {
  getPosts(type?: string) {
    return apiClient
      .get<ResData<CommunityPost[]>>('/fan-community/posts', { params: { type } })
      .then(unwrapResponse)
  },

  getPost(id: number) {
    return apiClient
      .get<ResData<CommunityPost>>(`/fan-community/posts/${id}`)
      .then(unwrapResponse)
  },

  createPost(request: CreatePostRequest) {
    return apiClient
      .post<ResData<CommunityPost>>('/fan-community/posts', request)
      .then(unwrapResponse)
  },

  updatePost(id: number, request: Partial<CreatePostRequest>) {
    return apiClient
      .put<ResData<CommunityPost>>(`/fan-community/posts/${id}`, request)
      .then(unwrapResponse)
  },

  deletePost(id: number) {
    return apiClient
      .delete<ResData<void>>(`/fan-community/posts/${id}`)
      .then(unwrapResponse)
  },

  likePost(id: number) {
    return apiClient
      .post<ResData<void>>(`/fan-community/posts/${id}/like`)
      .then(unwrapResponse)
  },

  pinPost(id: number) {
    return apiClient
      .post<ResData<void>>(`/fan-community/posts/${id}/pin`)
      .then(unwrapResponse)
  },

  getComments(postId: number) {
    return apiClient
      .get<ResData<PostComment[]>>(`/fan-community/posts/${postId}/comments`)
      .then(unwrapResponse)
  },

  createComment(postId: number, content: string) {
    return apiClient
      .post<ResData<PostComment>>(`/fan-community/posts/${postId}/comments`, { content })
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<CommunitySummary>>('/fan-community/summary')
      .then(unwrapResponse)
  },
}
