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
 * Export AI performance report to PDF
 */
export async function exportReportToPDF(report: {
  reportMarkdown: string
  highlights: string[]
  improvements: string[]
  nextWeekSuggestions: string[]
}, meta: {
  period: string
  generatedAt?: string
}): Promise<void> {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })

  const PAGE_WIDTH = 210
  const MARGIN = 20
  const CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
  let y = MARGIN

  // Helper: check page break
  function checkPageBreak(needed: number) {
    if (y + needed > 280) {
      doc.addPage()
      y = MARGIN
    }
  }

  // Helper: draw wrapped text, returns new y
  function drawWrapped(text: string, x: number, currentY: number, maxWidth: number, lineHeight: number): number {
    const lines = doc.splitTextToSize(text, maxWidth) as string[]
    for (const line of lines) {
      checkPageBreak(lineHeight)
      doc.text(line, x, currentY)
      currentY += lineHeight
    }
    return currentY
  }

  // === Header ===
  doc.setFillColor(124, 58, 237) // primary purple
  doc.rect(0, 0, PAGE_WIDTH, 40, 'F')
  doc.setTextColor(255, 255, 255)
  doc.setFontSize(22)
  doc.text('onGo 성과 리포트', MARGIN, 20)
  doc.setFontSize(10)
  doc.text(`분석 기간: ${meta.period}`, MARGIN, 30)
  const dateStr = meta.generatedAt || new Date().toLocaleDateString('ko-KR')
  doc.text(`생성일: ${dateStr}`, PAGE_WIDTH - MARGIN - 40, 30)
  y = 50

  // === Highlights Section ===
  doc.setTextColor(16, 185, 129) // green
  doc.setFontSize(14)
  doc.text('하이라이트', MARGIN, y)
  y += 8
  doc.setTextColor(60, 60, 60)
  doc.setFontSize(10)
  for (const item of report.highlights) {
    y = drawWrapped(`- ${item}`, MARGIN + 4, y, CONTENT_WIDTH - 4, 6)
  }
  y += 6

  // === Improvements Section ===
  checkPageBreak(20)
  doc.setTextColor(239, 68, 68) // red
  doc.setFontSize(14)
  doc.text('개선 영역', MARGIN, y)
  y += 8
  doc.setTextColor(60, 60, 60)
  doc.setFontSize(10)
  for (const item of report.improvements) {
    y = drawWrapped(`- ${item}`, MARGIN + 4, y, CONTENT_WIDTH - 4, 6)
  }
  y += 6

  // === Next Week Suggestions ===
  checkPageBreak(20)
  doc.setTextColor(59, 130, 246) // blue
  doc.setFontSize(14)
  doc.text('다음 주 제안', MARGIN, y)
  y += 8
  doc.setTextColor(60, 60, 60)
  doc.setFontSize(10)
  for (const item of report.nextWeekSuggestions) {
    y = drawWrapped(`- ${item}`, MARGIN + 4, y, CONTENT_WIDTH - 4, 6)
  }
  y += 10

  // === Full Markdown Report ===
  checkPageBreak(20)
  doc.setTextColor(124, 58, 237)
  doc.setFontSize(14)
  doc.text('상세 리포트', MARGIN, y)
  y += 8

  // Parse markdown into lines
  const markdownLines = report.reportMarkdown.split('\n')
  for (const line of markdownLines) {
    const trimmed = line.trim()
    if (!trimmed) {
      y += 3
      continue
    }

    if (trimmed.startsWith('### ')) {
      checkPageBreak(12)
      doc.setFontSize(11)
      doc.setTextColor(80, 80, 80)
      y += 2
      doc.text(trimmed.replace('### ', ''), MARGIN, y)
      y += 6
    } else if (trimmed.startsWith('## ')) {
      checkPageBreak(14)
      doc.setFontSize(13)
      doc.setTextColor(60, 60, 60)
      y += 4
      doc.text(trimmed.replace('## ', ''), MARGIN, y)
      y += 7
    } else if (trimmed.startsWith('# ')) {
      checkPageBreak(16)
      doc.setFontSize(15)
      doc.setTextColor(40, 40, 40)
      y += 5
      doc.text(trimmed.replace('# ', ''), MARGIN, y)
      y += 8
    } else if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      doc.setFontSize(10)
      doc.setTextColor(60, 60, 60)
      const text = trimmed.replace(/^[-*] /, '- ')
      y = drawWrapped(text, MARGIN + 4, y, CONTENT_WIDTH - 4, 5.5)
    } else if (/^\d+\. /.test(trimmed)) {
      doc.setFontSize(10)
      doc.setTextColor(60, 60, 60)
      y = drawWrapped(trimmed, MARGIN + 4, y, CONTENT_WIDTH - 4, 5.5)
    } else {
      doc.setFontSize(10)
      doc.setTextColor(60, 60, 60)
      // Strip bold markers
      const clean = trimmed.replace(/\*\*(.+?)\*\*/g, '$1')
      y = drawWrapped(clean, MARGIN, y, CONTENT_WIDTH, 5.5)
    }
  }

  // === Footer ===
  const pageCount = doc.getNumberOfPages()
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(8)
    doc.setTextColor(160, 160, 160)
    doc.text(
      `onGo - 크리에이터 성과 리포트 | 페이지 ${i}/${pageCount}`,
      PAGE_WIDTH / 2,
      292,
      { align: 'center' },
    )
  }

  // Trigger download
  const filename = `onGo_성과리포트_${meta.period.replace(/\s/g, '_')}_${new Date().toISOString().slice(0, 10)}.pdf`
  doc.save(filename)
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
