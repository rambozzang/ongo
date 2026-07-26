import { computed, ref, toValue, watch, type MaybeRefOrGetter, type Ref } from 'vue'

/**
 * 목록 화면 공통 제어 로직 — 검색 · 정렬 · 다중 선택을 한 번에 얹는다.
 *
 * 백엔드가 페이지네이션/검색/정렬을 지원하지 않는 목록(대부분의 CRUD 화면)을 전제로
 * **전부 클라이언트 사이드**에서 처리한다.
 *
 * ```ts
 * const { query, filtered, selectedIds, toggle } = useListControls(() => store.items, {
 *   searchFields: ['title', 'description', 'tags'],
 *   sortOptions: computed(() => [
 *     { key: 'recent', label: '최근순', accessor: 'updatedAt', kind: 'date', defaultDir: 'desc' },
 *   ]),
 *   defaultSortKey: 'recent',
 * })
 * ```
 *
 * 설계 규칙
 * - `query`는 입력값을 **가공하지 않고 그대로** 담는다. 트림/소문자화는 파생 computed에서만 한다.
 *   (한글 IME 조합 중 부모가 값을 바꿔 되돌려주면 조합이 깨지므로, 원문 왕복이 필수다.)
 * - 한글은 NFC로 정규화한 뒤 비교한다. macOS 등에서 넘어온 NFD 문자열도 검색된다.
 * - 검색어는 공백으로 잘라 **모든 토큰을 만족(AND)** 하는 항목만 남긴다.
 * - 선택 상태는 목록이 바뀔 때마다 자동으로 정리된다(아래 `selectionScope` 참고).
 */

/** 목록 항목 식별자 타입 — 이 프로젝트는 전 스키마가 BIGINT(number)다. */
export type ListItemId = string | number

export type SortDirection = 'asc' | 'desc'

/** 정렬 비교 방식. 생략하면 첫 유효값을 보고 추론한다. */
export type SortValueKind = 'string' | 'number' | 'date'

/** 검색/정렬 대상 값 추출기. 프로퍼티 키 또는 함수. */
export type FieldAccessor<T> = keyof T | ((item: T) => unknown)

export interface ListSortOption<T> {
  /** 정렬 식별자 (`sortKey`에 저장되는 값) */
  key: string
  /** 드롭다운에 표시될 라벨 */
  label: string
  /** 비교할 값 추출기 */
  accessor: FieldAccessor<T>
  /** 비교 방식. 생략 시 자동 추론 */
  kind?: SortValueKind
  /** 이 정렬을 고를 때 적용될 기본 방향 (생략 시 'asc') */
  defaultDir?: SortDirection
}

export interface UseListControlsOptions<T, Id extends ListItemId = number> {
  /** 검색 대상 필드. 배열 값(태그 등)은 자동으로 펼쳐서 매칭한다. */
  searchFields?: FieldAccessor<T>[]
  /** 정렬 옵션 목록. i18n 라벨을 쓰려면 computed로 넘긴다. */
  sortOptions?: MaybeRefOrGetter<ListSortOption<T>[]>
  /** 초기 정렬 키 (생략 시 첫 번째 옵션) */
  defaultSortKey?: string
  /** 초기 정렬 방향 (생략 시 해당 옵션의 defaultDir) */
  defaultSortDir?: SortDirection
  /** 검색 이전에 적용할 추가 조건들 (탭/상태 필터 등). 전부 통과해야 남는다. */
  filters?: MaybeRefOrGetter<Array<(item: T) => boolean>>
  /** 항목 식별자 추출기 (기본값 `item.id`) */
  getId?: (item: T) => Id
  /**
   * 선택 상태 유지 범위.
   * - `'visible'`(기본): 화면에 보이는 목록에서 사라지면 선택도 해제된다.
   *   → 일괄 작업이 "보이지 않는 항목"까지 건드리는 사고를 원천 차단한다.
   * - `'all'`: 검색/필터로 잠시 가려져도 선택을 유지하고, 원본에서 삭제될 때만 정리한다.
   */
  selectionScope?: 'visible' | 'all'
}

/** 로케일 기본 정렬기. 숫자 포함 문자열("2화" < "10화")도 자연스럽게 정렬된다. */
const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' })

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}([T ]|$)/

const isBlank = (value: unknown): boolean => value === null || value === undefined || value === ''

const readField = <T>(item: T, accessor: FieldAccessor<T>): unknown =>
  typeof accessor === 'function' ? accessor(item) : item[accessor]

/** 검색용 문자열화 — 배열은 펼치고, 객체는 무시한다. */
const toSearchText = (value: unknown): string => {
  if (isBlank(value)) return ''
  if (Array.isArray(value)) return value.map(toSearchText).join(' ')
  if (value instanceof Date) return value.toISOString()
  if (typeof value === 'object') return ''
  return String(value)
}

/** 대소문자 무시 + 한글 NFC 정규화. */
const normalize = (value: string): string => value.normalize('NFC').toLowerCase()

const toNumber = (value: unknown): number => {
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

const toTime = (value: unknown): number => {
  if (value instanceof Date) return value.getTime()
  const parsed = new Date(String(value)).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

const inferKind = <T>(items: T[], accessor: FieldAccessor<T>): SortValueKind => {
  for (const item of items) {
    const value = readField(item, accessor)
    if (isBlank(value)) continue
    if (typeof value === 'number') return 'number'
    if (value instanceof Date) return 'date'
    if (typeof value === 'string' && ISO_DATE_PATTERN.test(value)) return 'date'
    return 'string'
  }
  return 'string'
}

const compareValues = (a: unknown, b: unknown, kind: SortValueKind): number => {
  if (kind === 'number') return toNumber(a) - toNumber(b)
  if (kind === 'date') return toTime(a) - toTime(b)
  return collator.compare(String(a), String(b))
}

export function useListControls<T, Id extends ListItemId = number>(
  source: MaybeRefOrGetter<T[]>,
  options: UseListControlsOptions<T, Id> = {},
) {
  const {
    searchFields = [],
    defaultSortKey,
    defaultSortDir,
    getId = (item: T) => (item as unknown as { id: Id }).id,
    selectionScope = 'visible',
  } = options

  const items = computed<T[]>(() => toValue(source) ?? [])
  const sortOptions = computed<ListSortOption<T>[]>(() => toValue(options.sortOptions) ?? [])
  const filters = computed(() => toValue(options.filters) ?? [])

  // --- 검색 ------------------------------------------------------------------
  // 입력 원문 그대로. IME 조합 중 값을 되돌려 쓰지 않기 위해 가공하지 않는다.
  const query = ref('')

  const queryTokens = computed(() => {
    const trimmed = query.value.trim()
    if (!trimmed) return []
    return normalize(trimmed).split(/\s+/).filter(Boolean)
  })

  const hasQuery = computed(() => queryTokens.value.length > 0)

  const buildHaystack = (item: T): string =>
    normalize(searchFields.map((field) => toSearchText(readField(item, field))).join(' '))

  // --- 정렬 ------------------------------------------------------------------
  const firstKey = () => defaultSortKey ?? sortOptions.value[0]?.key ?? ''
  const sortKeyState = ref<string>(firstKey())
  const sortDirState = ref<SortDirection>(
    defaultSortDir ??
      sortOptions.value.find((option) => option.key === sortKeyState.value)?.defaultDir ??
      'asc',
  )

  /**
   * 정렬 키를 바꾸면 그 옵션이 선언한 기본 방향으로 함께 전환된다.
   * (마감일은 오름차순, 최신순은 내림차순이 자연스럽다.)
   */
  const sortKey = computed<string>({
    get: () => sortKeyState.value,
    set: (key) => {
      if (key === sortKeyState.value) return
      sortKeyState.value = key
      sortDirState.value = sortOptions.value.find((option) => option.key === key)?.defaultDir ?? 'asc'
    },
  })

  const sortDir = computed<SortDirection>({
    get: () => sortDirState.value,
    set: (dir) => {
      sortDirState.value = dir
    },
  })

  const activeSortOption = computed(
    () => sortOptions.value.find((option) => option.key === sortKeyState.value) ?? null,
  )

  const toggleSortDir = () => {
    sortDirState.value = sortDirState.value === 'asc' ? 'desc' : 'asc'
  }

  // --- 필터 + 검색 + 정렬 ----------------------------------------------------
  const filtered = computed<T[]>(() => {
    const predicates = filters.value
    const tokens = queryTokens.value

    const matched = items.value.filter((item) => {
      if (predicates.some((predicate) => !predicate(item))) return false
      if (tokens.length === 0) return true
      const haystack = buildHaystack(item)
      return tokens.every((token) => haystack.includes(token))
    })

    const option = activeSortOption.value
    if (!option) return matched

    const kind = option.kind ?? inferKind(matched, option.accessor)
    const direction = sortDirState.value === 'desc' ? -1 : 1

    // filter()가 이미 새 배열을 만들었으므로 원본을 건드리지 않는다.
    return matched.sort((a, b) => {
      const aValue = readField(a, option.accessor)
      const bValue = readField(b, option.accessor)
      // 빈 값은 방향과 무관하게 항상 뒤로 보낸다.
      if (isBlank(aValue) && isBlank(bValue)) return 0
      if (isBlank(aValue)) return 1
      if (isBlank(bValue)) return -1
      return compareValues(aValue, bValue, kind) * direction
    })
  })

  // --- 다중 선택 -------------------------------------------------------------
  const selectedIds = ref(new Set<Id>()) as Ref<Set<Id>>

  /** 선택이 살아남을 수 있는 id 집합. 이 밖으로 나간 id는 자동 정리된다. */
  const selectableIds = computed(() => {
    const scoped = selectionScope === 'all' ? items.value : filtered.value
    return new Set<Id>(scoped.map(getId))
  })

  /**
   * 삭제·필터 변경 등으로 목록에서 사라진 id를 선택 상태에서 걷어낸다.
   * 이게 없으면 "유령 선택"이 남아 일괄 작업이 존재하지 않는 항목을 건드린다.
   */
  watch(selectableIds, (alive) => {
    if (selectedIds.value.size === 0) return
    const next = new Set<Id>()
    selectedIds.value.forEach((id) => {
      if (alive.has(id)) next.add(id)
    })
    if (next.size !== selectedIds.value.size) selectedIds.value = next
  })

  const selectedCount = computed(() => selectedIds.value.size)

  const selectedItems = computed(() => items.value.filter((item) => selectedIds.value.has(getId(item))))

  const allSelected = computed(
    () => filtered.value.length > 0 && filtered.value.every((item) => selectedIds.value.has(getId(item))),
  )

  const someSelected = computed(() => selectedCount.value > 0 && !allSelected.value)

  const isSelected = (id: Id): boolean => selectedIds.value.has(id)

  const toggle = (id: Id) => {
    const next = new Set(selectedIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    selectedIds.value = next
  }

  /** 현재 보이는 목록 전체를 선택/해제한다. */
  const toggleAll = () => {
    const next = new Set(selectedIds.value)
    if (allSelected.value) filtered.value.forEach((item) => next.delete(getId(item)))
    else filtered.value.forEach((item) => next.add(getId(item)))
    selectedIds.value = next
  }

  const clearSelection = () => {
    if (selectedIds.value.size === 0) return
    selectedIds.value = new Set<Id>()
  }

  // --- 빈 상태 구분 ----------------------------------------------------------
  /** 데이터 자체가 없음 → "첫 항목을 만들어보세요" */
  const isSourceEmpty = computed(() => items.value.length === 0)
  /** 검색/필터 결과만 없음 → "검색어를 바꿔보세요" */
  const isResultEmpty = computed(() => !isSourceEmpty.value && filtered.value.length === 0)

  const resetFilters = () => {
    query.value = ''
  }

  return {
    // 검색
    query,
    hasQuery,
    // 정렬
    sortKey,
    sortDir,
    sortOptions,
    toggleSortDir,
    // 결과
    filtered,
    totalCount: computed(() => items.value.length),
    visibleCount: computed(() => filtered.value.length),
    isSourceEmpty,
    isResultEmpty,
    resetFilters,
    // 선택
    selectedIds,
    selectedItems,
    selectedCount,
    allSelected,
    someSelected,
    isSelected,
    toggle,
    toggleAll,
    clearSelection,
  }
}
