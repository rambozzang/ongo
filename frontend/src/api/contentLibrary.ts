import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { LibraryItem, LibraryFolder, ContentLibrarySummary } from '@/types/contentLibrary'

export const contentLibraryApi = {
  getItems: (folderId?: number, type?: string) =>
    apiClient.get<ResData<LibraryItem[]>>('/content-library', { params: { folderId, type } }).then(unwrapResponse),
  getItem: (id: number) =>
    apiClient.get<ResData<LibraryItem>>(`/content-library/${id}`).then(unwrapResponse),
  getFolders: () =>
    apiClient.get<ResData<LibraryFolder[]>>('/content-library/folders').then(unwrapResponse),
  createFolder: (name: string, parentId?: number) =>
    apiClient.post<ResData<LibraryFolder>>('/content-library/folders', { name, parentId }).then(unwrapResponse),
  deleteItem: (id: number) =>
    apiClient.delete<ResData<void>>(`/content-library/${id}`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<ContentLibrarySummary>>('/content-library/summary').then(unwrapResponse),
}

export default contentLibraryApi
