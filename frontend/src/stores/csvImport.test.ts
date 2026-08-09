import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCsvImportStore } from './csvImport'
import { csvImportApi } from '@/api/csvImport'
import { scheduleApi } from '@/api/schedule'

vi.mock('@/api/csvImport', () => ({
  csvImportApi: { upload: vi.fn() },
}))

vi.mock('@/api/schedule', () => ({
  scheduleApi: { create: vi.fn() },
}))

class TestFileReader {
  static content = ''
  onload: ((event: { target: { result: string } }) => void) | null = null
  onerror: (() => void) | null = null

  readAsText() {
    queueMicrotask(() => this.onload?.({ target: { result: TestFileReader.content } }))
  }
}

const csvFile = (content: string, name = 'schedule.csv') => {
  TestFileReader.content = content
  return new File([content], name, { type: 'text/csv' })
}

describe('CSV schedule import store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.stubGlobal('FileReader', TestFileReader)
  })

  it('parses quoted fields and classifies valid, warning, and invalid rows', async () => {
    const longDescription = 'x'.repeat(5001)
    const content = [
      'title,description,tags,platforms,scheduledAt,visibility',
      '브이로그,"쉼표, 포함",일상;여행,YT;TT,2099-03-01 14:00,public',
      `긴 설명,${longDescription},태그,YT,2099-03-02 14:00,public`,
      ',설명,태그,UNKNOWN,잘못된 날짜,private',
    ].join('\n')
    const store = useCsvImportStore()

    await expect(store.parseFile(csvFile(content))).resolves.toBeUndefined()
    expect(store.parsedData?.totalRows).toBe(3)
    expect(store.parsedData?.validRows).toBe(1)
    expect(store.parsedData?.warningRows).toBe(1)
    expect(store.parsedData?.errorRows).toBe(1)
    expect(store.parsedData?.rows[0]).toMatchObject({
      title: '브이로그',
      description: '쉼표, 포함',
      tags: ['일상', '여행'],
      platforms: ['YT', 'TT'],
      status: 'valid',
    })
    expect(store.parsedData?.rows[1].warnings).toContain('설명이 5000자를 초과합니다. 일부 플랫폼에서 잘릴 수 있습니다.')
    expect(store.hasData).toBe(true)
    expect(store.importableRows).toHaveLength(2)
    expect(store.templateColumns).toHaveLength(6)
  })

  it('rejects unsupported, empty, and header-only files with an actionable error', async () => {
    const store = useCsvImportStore()
    await expect(store.parseFile(csvFile('title\nvideo', 'schedule.txt'))).rejects.toThrow('CSV 파일만 가능합니다')
    expect(store.parseError).toContain('CSV 파일만')

    await expect(store.parseFile(csvFile(''))).rejects.toThrow('파일이 비어 있습니다')
    await expect(store.parseFile(csvFile('title,description'))).rejects.toThrow('데이터가 없습니다')
  })

  it('revalidates edited rows and updates aggregate counts when a row is removed', async () => {
    const store = useCsvImportStore()
    await store.parseFile(csvFile([
      'title,description,tags,platforms,scheduledAt,visibility',
      '초안,,tag,YT,2099-03-01 14:00,public',
      '삭제할 행,,tag,YT,2099-03-02 14:00,public',
    ].join('\n')))

    store.editRow(2, 'title', '')
    expect(store.parsedData?.rows[0].status).toBe('error')
    expect(store.parsedData?.errorRows).toBe(1)
    store.editRow(2, 'title', '수정된 제목')
    store.editRow(2, 'tags', '하나; 둘')
    store.editRow(2, 'platforms', 'ig;nv')
    store.editRow(2, 'visibility', 'PRIVATE')
    expect(store.parsedData?.rows[0]).toMatchObject({
      title: '수정된 제목',
      tags: ['하나', '둘'],
      platforms: ['IG', 'NV'],
      visibility: 'private',
      status: 'valid',
    })

    store.removeRow(3)
    expect(store.parsedData?.totalRows).toBe(1)
    expect(store.parsedData?.validRows).toBe(1)
    store.clearData()
    expect(store.parsedData).toBeNull()
    expect(store.hasData).toBe(false)
    expect(store.importProgress).toBe(0)
  })

  it('uses the server CSV import and annotates row-level server errors', async () => {
    vi.mocked(csvImportApi.upload).mockResolvedValue({
      importedCount: 1,
      errors: [{ rowNumber: 3, message: '영상 파일이 없습니다.' }],
    } as never)
    const store = useCsvImportStore()
    await store.parseFile(csvFile([
      'title,description,tags,platforms,scheduledAt,visibility',
      '첫 예약,,tag,YT,2099-03-01 14:00,public',
      '둘째 예약,,tag,IG,2099-03-02 14:00,public',
    ].join('\n')))

    await store.importSchedules()
    expect(csvImportApi.upload).toHaveBeenCalledOnce()
    expect(store.parsedData?.rows[1].status).toBe('error')
    expect(store.parsedData?.rows[1].errors).toContain('영상 파일이 없습니다.')
    expect(store.importing).toBe(false)
    expect(store.importProgress).toBe(100)
  })

  it('falls back to row-by-row scheduling and keeps failed rows visible', async () => {
    vi.mocked(csvImportApi.upload).mockRejectedValue(new Error('bulk endpoint unavailable'))
    vi.mocked(scheduleApi.create)
      .mockResolvedValueOnce(undefined as never)
      .mockRejectedValueOnce(new Error('schedule rejected'))
    const store = useCsvImportStore()
    await store.parseFile(csvFile([
      'title,description,tags,platforms,scheduledAt,visibility',
      '첫 예약,,tag,YT;TT,2099-03-01 14:00,public',
      '둘째 예약,,tag,IG,2099-03-02 14:00,public',
    ].join('\n')))

    await store.importSchedules()
    expect(scheduleApi.create).toHaveBeenNthCalledWith(1, {
      videoId: 0,
      scheduledAt: '2099-03-01T14:00:00',
      platforms: [{ platform: 'YOUTUBE' }, { platform: 'TIKTOK' }],
    })
    expect(store.parsedData?.rows[1].status).toBe('error')
    expect(store.parsedData?.rows[1].errors).toContain('서버 등록 실패')
    expect(store.importing).toBe(false)
    expect(store.importProgress).toBe(100)
  })
})
