export interface MoodBoard {
  id: number
  name: string
  description: string
  category: string
  itemCount: number
  coverImage: string | null
  tags: string[]
  isPublic: boolean
  createdAt: string
}

export interface MoodBoardItem {
  id: number
  boardId: number
  type: string
  title: string
  imageUrl: string | null
  sourceUrl: string | null
  note: string | null
  color: string | null
  position: number
}

export interface CreateMoodBoardRequest {
  name: string
  description: string
  category: string
  tags: string[]
}

export interface MoodBoardSummary {
  totalBoards: number
  totalItems: number
  topCategory: string
  recentBoard: string
  avgItemsPerBoard: number
}
