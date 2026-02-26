export interface LibraryItem {
  id: number
  title: string
  type: 'VIDEO' | 'IMAGE' | 'AUDIO' | 'DOCUMENT'
  platform: string
  thumbnailUrl: string
  fileSize: number
  tags: string[]
  folderId: number | null
  folderName: string | null
  uploadedAt: string
  updatedAt: string
}

export interface LibraryFolder {
  id: number
  name: string
  parentId: number | null
  itemCount: number
  color: string
  createdAt: string
}

export interface ContentLibrarySummary {
  totalItems: number
  totalFolders: number
  totalSize: number
  videoCount: number
  imageCount: number
}
