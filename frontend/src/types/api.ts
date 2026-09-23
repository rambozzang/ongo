export interface ResData<T> {
  success: boolean
  message: string | null
  data: T | null
  error: string | null
  periodLimit?: {
    requestedDays: number
    appliedDays: number
    maxDays: number
    wasTruncated: boolean
  } | null
}

export interface PageRequest {
  page: number
  size: number
  sort?: string
  direction?: 'ASC' | 'DESC'
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface DateRange {
  startDate: string
  endDate: string
}
