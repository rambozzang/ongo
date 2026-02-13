export type CsvValidationStatus = 'valid' | 'warning' | 'error'

export interface CsvScheduleRow {
  rowNumber: number
  title: string
  description: string
  tags: string[]
  platforms: string[]
  scheduledAt: string
  visibility: string
  status: CsvValidationStatus
  errors: string[]
  warnings: string[]
}

export interface CsvImportResult {
  totalRows: number
  validRows: number
  warningRows: number
  errorRows: number
  rows: CsvScheduleRow[]
}

export interface CsvTemplateColumn {
  key: string
  label: string
  required: boolean
  description: string
  example: string
}
