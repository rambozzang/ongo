import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  CsvScheduleRow,
  CsvImportResult,
  CsvTemplateColumn,
  CsvValidationStatus,
} from '@/types/csvImport'
import { csvImportApi } from '@/api/csvImport'
import type { Platform } from '@/types/channel'

const VALID_PLATFORMS = ['YT', 'TT', 'IG', 'NV']
const VALID_VISIBILITIES = ['public', 'private', 'unlisted']
const DATE_REGEX = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/

export const useCsvImportStore = defineStore('csvImport', () => {
  const parsedData = ref<CsvImportResult | null>(null)
  const importing = ref(false)
  const importProgress = ref(0)
  const parseError = ref<string | null>(null)

  const templateColumns = computed<CsvTemplateColumn[]>(() => [
    {
      key: 'title',
      label: '제목',
      required: true,
      description: '영상 제목 (최대 100자)',
      example: '나의 첫 브이로그',
    },
    {
      key: 'description',
      label: '설명',
      required: false,
      description: '영상 설명 (최대 5000자)',
      example: '오늘의 일상을 담았습니다.',
    },
    {
      key: 'tags',
      label: '태그(;구분)',
      required: false,
      description: '세미콜론(;)으로 구분된 태그 목록',
      example: '브이로그;일상;여행',
    },
    {
      key: 'platforms',
      label: '플랫폼(YT;TT;IG;NV)',
      required: true,
      description: 'YT=YouTube, TT=TikTok, IG=Instagram, NV=Naver Clip (;구분)',
      example: 'YT;TT;IG',
    },
    {
      key: 'scheduledAt',
      label: '예약일시(YYYY-MM-DD HH:mm)',
      required: true,
      description: '예약 게시 일시 (형식: YYYY-MM-DD HH:mm)',
      example: '2026-03-01 14:00',
    },
    {
      key: 'visibility',
      label: '공개설정(public/private/unlisted)',
      required: true,
      description: 'public=공개, private=비공개, unlisted=일부공개',
      example: 'public',
    },
  ])

  const hasData = computed(() => parsedData.value !== null && parsedData.value.rows.length > 0)

  const importableRows = computed(() => {
    if (!parsedData.value) return []
    return parsedData.value.rows.filter((row) => row.status !== 'error')
  })

  function parseFile(file: File): Promise<void> {
    return new Promise((resolve, reject) => {
      parseError.value = null
      parsedData.value = null

      const extension = file.name.split('.').pop()?.toLowerCase()
      if (!extension || extension !== 'csv') {
        parseError.value = '지원하지 않는 파일 형식입니다. CSV 파일만 가능합니다.'
        reject(new Error(parseError.value))
        return
      }

      const reader = new FileReader()

      reader.onload = (event) => {
        try {
          const text = event.target?.result as string
          if (!text || text.trim().length === 0) {
            parseError.value = '파일이 비어 있습니다.'
            reject(new Error(parseError.value))
            return
          }

          const lines = text.split('\n').filter((line) => line.trim().length > 0)

          if (lines.length < 2) {
            parseError.value = '데이터가 없습니다. 헤더 행 이후 최소 1개의 데이터 행이 필요합니다.'
            reject(new Error(parseError.value))
            return
          }

          const dataLines = lines.slice(1)
          const rows: CsvScheduleRow[] = []

          for (let i = 0; i < dataLines.length; i++) {
            const columns = parseCsvLine(dataLines[i])
            const row = parseRow(i + 2, columns)
            rows.push(row)
          }

          const validRows = rows.filter((r) => r.status === 'valid').length
          const warningRows = rows.filter((r) => r.status === 'warning').length
          const errorRows = rows.filter((r) => r.status === 'error').length

          parsedData.value = {
            totalRows: rows.length,
            validRows,
            warningRows,
            errorRows,
            rows,
          }

          resolve()
        } catch {
          parseError.value = '파일 파싱 중 오류가 발생했습니다.'
          reject(new Error(parseError.value))
        }
      }

      reader.onerror = () => {
        parseError.value = '파일을 읽는 중 오류가 발생했습니다.'
        reject(new Error(parseError.value))
      }

      reader.readAsText(file, 'UTF-8')
    })
  }

  function parseCsvLine(line: string): string[] {
    const result: string[] = []
    let current = ''
    let inQuotes = false

    for (let i = 0; i < line.length; i++) {
      const char = line[i]
      if (char === '"') {
        if (inQuotes && i + 1 < line.length && line[i + 1] === '"') {
          current += '"'
          i++
        } else {
          inQuotes = !inQuotes
        }
      } else if (char === ',' && !inQuotes) {
        result.push(current.trim())
        current = ''
      } else {
        current += char
      }
    }
    result.push(current.trim())
    return result
  }

  function parseRow(rowNumber: number, columns: string[]): CsvScheduleRow {
    const errors: string[] = []
    const warnings: string[] = []

    const title = columns[0] || ''
    const description = columns[1] || ''
    const tagsRaw = columns[2] || ''
    const platformsRaw = columns[3] || ''
    const scheduledAt = columns[4] || ''
    const visibility = columns[5]?.toLowerCase() || ''

    const tags = tagsRaw
      .split(';')
      .map((t) => t.trim())
      .filter((t) => t.length > 0)
    const platforms = platformsRaw
      .split(';')
      .map((p) => p.trim().toUpperCase())
      .filter((p) => p.length > 0)

    if (!title) {
      errors.push('제목은 필수 항목입니다.')
    } else if (title.length > 100) {
      errors.push('제목은 100자 이내여야 합니다.')
    }

    if (description.length > 5000) {
      warnings.push('설명이 5000자를 초과합니다. 일부 플랫폼에서 잘릴 수 있습니다.')
    }

    if (platforms.length === 0) {
      errors.push('최소 1개의 플랫폼을 지정해야 합니다.')
    } else {
      const invalidPlatforms = platforms.filter((p) => !VALID_PLATFORMS.includes(p))
      if (invalidPlatforms.length > 0) {
        errors.push(`유효하지 않은 플랫폼: ${invalidPlatforms.join(', ')} (YT, TT, IG, NV만 가능)`)
      }
    }

    if (!scheduledAt) {
      errors.push('예약일시는 필수 항목입니다.')
    } else if (!DATE_REGEX.test(scheduledAt)) {
      errors.push('예약일시 형식이 올바르지 않습니다. (YYYY-MM-DD HH:mm)')
    } else {
      const parsedDate = new Date(scheduledAt.replace(' ', 'T'))
      if (isNaN(parsedDate.getTime())) {
        errors.push('유효하지 않은 날짜입니다.')
      } else if (parsedDate <= new Date()) {
        warnings.push('예약일시가 현재 시간 이전입니다.')
      }
    }

    if (!visibility) {
      errors.push('공개설정은 필수 항목입니다.')
    } else if (!VALID_VISIBILITIES.includes(visibility)) {
      errors.push(
        `유효하지 않은 공개설정: ${visibility} (public, private, unlisted만 가능)`,
      )
    }

    if (tags.length > 30) {
      warnings.push('태그가 30개를 초과합니다. 일부 플랫폼에서 제한될 수 있습니다.')
    }

    let status: CsvValidationStatus = 'valid'
    if (errors.length > 0) {
      status = 'error'
    } else if (warnings.length > 0) {
      status = 'warning'
    }

    return {
      rowNumber,
      title,
      description,
      tags,
      platforms,
      scheduledAt,
      visibility,
      status,
      errors,
      warnings,
    }
  }

  function validateRows() {
    if (!parsedData.value) return

    const rows = parsedData.value.rows.map((row) => {
      const columns = [
        row.title,
        row.description,
        row.tags.join(';'),
        row.platforms.join(';'),
        row.scheduledAt,
        row.visibility,
      ]
      return parseRow(row.rowNumber, columns)
    })

    const validRows = rows.filter((r) => r.status === 'valid').length
    const warningRows = rows.filter((r) => r.status === 'warning').length
    const errorRows = rows.filter((r) => r.status === 'error').length

    parsedData.value = {
      totalRows: rows.length,
      validRows,
      warningRows,
      errorRows,
      rows,
    }
  }

  async function importSchedules(): Promise<void> {
    if (!parsedData.value) return

    importing.value = true
    importProgress.value = 0

    try {
      // Try server-side import first — construct a CSV blob from parsed data
      const header = 'title,description,tags,platforms,scheduledAt,visibility'
      const csvRows = importableRows.value.map((row) =>
        [
          row.title,
          row.description,
          row.tags.join(';'),
          row.platforms.join(';'),
          row.scheduledAt,
          row.visibility,
        ]
          .map((v) => `"${v.replace(/"/g, '""')}"`)
          .join(','),
      )
      const csvContent = [header, ...csvRows].join('\n')
      const blob = new Blob([csvContent], { type: 'text/csv' })
      const file = new File([blob], 'import.csv', { type: 'text/csv' })

      const result = await csvImportApi.upload(file)
      importProgress.value = 100

      // Mark rows with server errors
      if (result.errors && result.errors.length > 0) {
        for (const err of result.errors) {
          const row = parsedData.value.rows.find((r) => r.rowNumber === err.rowNumber)
          if (row) {
            row.status = 'error'
            row.errors.push(err.message)
          }
        }
      }
    } catch {
      // Fallback to row-by-row import via schedule API
      const { scheduleApi } = await import('@/api/schedule')
      const rowsToImport = importableRows.value
      const total = rowsToImport.length
      let processed = 0

      const platformMap: Record<string, Platform> = {
        YT: 'YOUTUBE',
        TT: 'TIKTOK',
        IG: 'INSTAGRAM',
        NV: 'NAVER_CLIP',
      }

      for (const row of rowsToImport) {
        try {
          await scheduleApi.create({
            videoId: 0,
            scheduledAt: row.scheduledAt.replace(' ', 'T') + ':00',
            platforms: row.platforms.map((p) => ({
              platform: platformMap[p] ?? (p as Platform),
            })),
          })
        } catch {
          row.status = 'error'
          row.errors.push('서버 등록 실패')
        }

        processed++
        importProgress.value = Math.round((processed / total) * 100)
      }
    } finally {
      importing.value = false
      importProgress.value = 100
    }
  }

  function clearData() {
    parsedData.value = null
    importing.value = false
    importProgress.value = 0
    parseError.value = null
  }

  function removeRow(rowNumber: number) {
    if (!parsedData.value) return

    parsedData.value.rows = parsedData.value.rows.filter((r) => r.rowNumber !== rowNumber)

    const rows = parsedData.value.rows
    parsedData.value.totalRows = rows.length
    parsedData.value.validRows = rows.filter((r) => r.status === 'valid').length
    parsedData.value.warningRows = rows.filter((r) => r.status === 'warning').length
    parsedData.value.errorRows = rows.filter((r) => r.status === 'error').length
  }

  function editRow(rowNumber: number, field: string, value: string) {
    if (!parsedData.value) return

    const row = parsedData.value.rows.find((r) => r.rowNumber === rowNumber)
    if (!row) return

    switch (field) {
      case 'title':
        row.title = value
        break
      case 'description':
        row.description = value
        break
      case 'tags':
        row.tags = value
          .split(';')
          .map((t) => t.trim())
          .filter((t) => t.length > 0)
        break
      case 'platforms':
        row.platforms = value
          .split(';')
          .map((p) => p.trim().toUpperCase())
          .filter((p) => p.length > 0)
        break
      case 'scheduledAt':
        row.scheduledAt = value
        break
      case 'visibility':
        row.visibility = value.toLowerCase()
        break
    }

    validateRows()
  }

  return {
    parsedData,
    importing,
    importProgress,
    parseError,
    templateColumns,
    hasData,
    importableRows,
    parseFile,
    validateRows,
    importSchedules,
    clearData,
    removeRow,
    editRow,
  }
})
