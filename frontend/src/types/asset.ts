export type AssetType = 'VIDEO' | 'IMAGE' | 'AUDIO' | 'TEMPLATE'

export interface Asset {
  id: number
  type: AssetType
  name: string
  fileUrl: string
  fileSize: number
  mimeType: string
  tags: string[]
  folderId: number | null
  thumbnail: string | null
  duration: number | null
  width: number | null
  height: number | null
  createdAt: string
}

export interface AssetFolder {
  id: number
  name: string
  parentId: number | null
  assetCount: number
  createdAt: string
}

export interface AssetFilter {
  type?: AssetType
  tags?: string[]
  folder?: number | null
  dateRange?: { startDate: string; endDate: string }
  search?: string
}

// Backend DTO types
export interface AssetResponse {
  id: number
  filename: string
  originalFilename: string | null
  fileUrl: string
  fileType: string
  fileSizeBytes: number | null
  mimeType: string | null
  tags: string[]
  folder: string
  width: number | null
  height: number | null
  durationSeconds: number | null
  createdAt: string | null
}

export interface AssetListResponse {
  assets: AssetResponse[]
  totalCount: number
}

export interface UpdateAssetRequest {
  tags?: string[]
  folder?: string
}
