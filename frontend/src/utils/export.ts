export interface ColumnDefinition<T> {
  key: keyof T | string
  header: string
  formatter?: (value: any, row?: T) => string
}

/**
 * Export data to CSV format with UTF-8 BOM for Korean character support
 */
export function exportToCSV<T>(
  data: T[],
  columns: ColumnDefinition<T>[],
  filename: string
): void {
  if (data.length === 0) {
    return
  }

  // Create CSV header
  const headers = columns.map((col) => col.header).join(',')

  // Create CSV rows
  const rows = data.map((row) => {
    return columns
      .map((col) => {
        const value = getNestedValue(row, col.key as string)
        const formatted = col.formatter ? col.formatter(value, row) : String(value ?? '')
        // Escape quotes and wrap in quotes if contains comma, newline, or quote
        const escaped = formatted.replace(/"/g, '""')
        return /[,"\n]/.test(escaped) ? `"${escaped}"` : escaped
      })
      .join(',')
  })

  // Combine headers and rows
  const csvContent = [headers, ...rows].join('\n')

  // Add UTF-8 BOM for proper Korean character encoding
  const BOM = '\uFEFF'
  const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' })

  // Trigger download
  downloadBlob(blob, filename)
}

/**
 * Export data to JSON format
 */
export function exportToJSON<T>(data: T[], filename: string): void {
  if (data.length === 0) {
    return
  }

  const jsonContent = JSON.stringify(data, null, 2)
  const blob = new Blob([jsonContent], { type: 'application/json;charset=utf-8;' })

  downloadBlob(blob, filename)
}

/**
 * Format date string for export (YYYY-MM-DD HH:mm:ss)
 */
export function formatDateForExport(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

/**
 * Format date string for export (YYYY-MM-DD only)
 */
export function formatDateOnlyForExport(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * Get nested value from object using dot notation
 */
function getNestedValue(obj: any, path: string): any {
  return path.split('.').reduce((current, key) => current?.[key], obj)
}

/**
 * Create a download link and trigger download
 */
function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
