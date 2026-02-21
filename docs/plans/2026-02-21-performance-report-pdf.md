# 성과 리포트 PDF 내보내기 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** AI 성과 인사이트 리포트를 브랜딩된 PDF로 다운로드할 수 있게 한다.

**Architecture:** 프론트엔드에서 jsPDF 라이브러리로 클라이언트 사이드 PDF 생성. 기존 `GenerateReportResponse` 데이터(마크다운 + highlights/improvements/suggestions 배열)를 구조화된 PDF로 변환. 백엔드 변경 없음.

**Tech Stack:** jsPDF (클라이언트 사이드 PDF), 기존 Vue 3 + TypeScript

---

### Task 1: jsPDF 의존성 설치

**Files:**
- Modify: `frontend/package.json`

**Step 1: npm install**

```bash
cd frontend && npm install jspdf
```

**Step 2: 타입 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS (jsPDF에 내장 타입 포함)

**Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: jsPDF 의존성 추가"
```

---

### Task 2: PDF 생성 유틸리티 함수 작성

**Files:**
- Modify: `frontend/src/utils/export.ts`

**Step 1: exportReportToPDF 함수 추가**

`export.ts` 파일 끝에 다음 함수를 추가한다. `GenerateReportResponse` 타입은 인라인으로 받아서 의존성을 최소화한다.

```typescript
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
  doc.text('✅ 하이라이트', MARGIN, y)
  y += 8
  doc.setTextColor(60, 60, 60)
  doc.setFontSize(10)
  for (const item of report.highlights) {
    y = drawWrapped(`• ${item}`, MARGIN + 4, y, CONTENT_WIDTH - 4, 6)
  }
  y += 6

  // === Improvements Section ===
  checkPageBreak(20)
  doc.setTextColor(239, 68, 68) // red
  doc.setFontSize(14)
  doc.text('⚠️ 개선 영역', MARGIN, y)
  y += 8
  doc.setTextColor(60, 60, 60)
  doc.setFontSize(10)
  for (const item of report.improvements) {
    y = drawWrapped(`• ${item}`, MARGIN + 4, y, CONTENT_WIDTH - 4, 6)
  }
  y += 6

  // === Next Week Suggestions ===
  checkPageBreak(20)
  doc.setTextColor(59, 130, 246) // blue
  doc.setFontSize(14)
  doc.text('💡 다음 주 제안', MARGIN, y)
  y += 8
  doc.setTextColor(60, 60, 60)
  doc.setFontSize(10)
  for (const item of report.nextWeekSuggestions) {
    y = drawWrapped(`• ${item}`, MARGIN + 4, y, CONTENT_WIDTH - 4, 6)
  }
  y += 10

  // === Full Markdown Report ===
  checkPageBreak(20)
  doc.setTextColor(124, 58, 237)
  doc.setFontSize(14)
  doc.text('📊 상세 리포트', MARGIN, y)
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
      const text = trimmed.replace(/^[-*] /, '• ')
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
```

**Step 2: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 3: Commit**

```bash
git add frontend/src/utils/export.ts
git commit -m "feat: PDF 성과 리포트 내보내기 유틸리티 함수 추가"
```

---

### Task 3: AnalyticsView에 PDF 다운로드 버튼 추가

**Files:**
- Modify: `frontend/src/views/AnalyticsView.vue`

**Step 1: import 추가**

`<script setup>` 상단 import 영역에 추가:

```typescript
import { exportReportToPDF } from '@/utils/export'
```

**Step 2: PDF 다운로드 핸들러 함수 추가**

`handleGenerateInsight` 함수 아래에:

```typescript
const pdfExporting = ref(false)

async function handleExportPDF() {
  if (!aiReport.value) return
  pdfExporting.value = true
  try {
    const periodLabel = period.value === '7d' ? '최근 7일' : period.value === '30d' ? '최근 30일' : '최근 90일'
    await exportReportToPDF(aiReport.value, {
      period: periodLabel,
    })
  } catch (e) {
    console.error('PDF 내보내기 실패:', e)
  } finally {
    pdfExporting.value = false
  }
}
```

**Step 3: 템플릿에 PDF 버튼 추가**

기존 AI 리포트 결과 영역에서 "다시 생성" 버튼 옆에 PDF 다운로드 버튼을 추가한다. 기존 코드:

```html
            <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
              <span>사용 크레딧: {{ aiReport.creditsUsed }} / 잔여: {{ aiReport.creditsRemaining }}</span>
              <button
                class="font-medium text-purple-600 hover:text-purple-800"
                @click="handleGenerateInsight"
              >
                다시 생성
              </button>
            </div>
```

변경 후:

```html
            <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
              <span>사용 크레딧: {{ aiReport.creditsUsed }} / 잔여: {{ aiReport.creditsRemaining }}</span>
              <div class="flex items-center gap-3">
                <button
                  class="inline-flex items-center gap-1 font-medium text-purple-600 hover:text-purple-800"
                  :disabled="pdfExporting"
                  @click="handleExportPDF"
                >
                  <ArrowDownTrayIcon class="h-3.5 w-3.5" />
                  {{ pdfExporting ? 'PDF 생성 중...' : 'PDF 다운로드' }}
                </button>
                <button
                  class="font-medium text-purple-600 hover:text-purple-800"
                  @click="handleGenerateInsight"
                >
                  다시 생성
                </button>
              </div>
            </div>
```

`ArrowDownTrayIcon`이 이미 import되어 있는지 확인. 없으면 heroicons import에 추가:

```typescript
import { ArrowDownTrayIcon } from '@heroicons/vue/24/outline'
```

**Step 4: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/views/AnalyticsView.vue
git commit -m "feat: AI 인사이트 리포트 PDF 다운로드 버튼 추가"
```

---

### Task 4: 전체 빌드 검증 및 최종 커밋

**Step 1: 프론트엔드 빌드**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 2: Vite 프로덕션 빌드 (번들 확인)**

```bash
cd frontend && npx vite build 2>&1 | tail -10
```

Expected: 빌드 성공, jsPDF가 번들에 포함됨

**Step 3: 최종 정리 커밋 (필요 시)**

변경사항이 있으면:

```bash
git add -A && git commit -m "chore: PDF 내보내기 최종 정리"
```
