import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface CsvImportResponse {
  totalRows: number
  successCount: number
  errorCount: number
  errors: CsvRowError[]
}

export interface CsvRowError {
  rowNumber: number
  message: string
}

export const csvImportApi = {
  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)

    return apiClient
      .post<ResData<CsvImportResponse>>('/videos/import/csv', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(unwrapResponse)
  },
}
