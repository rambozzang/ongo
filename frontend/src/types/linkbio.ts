export type BlockType = 'link' | 'header' | 'social' | 'video' | 'divider' | 'text'

/**
 * **서버가 실제로 저장하는 블록 타입.**
 *
 * 백엔드는 `link_bio_links` 한 테이블만 있고 DTO(`UpdateLinksRequest.links`)도 링크
 * 항목만 받는다. 나머지 타입은 도메인·저장소·스키마 어디에도 개념이 없다.
 *
 * 그래서 저장되지 않는 타입을 추가하면 **에디터에서는 성공한 것처럼 보이다가
 * 새로고침·공개 페이지에서 사라진다.** 그 흐름을 막기 위해 이 목록을 단일 출처로 두고
 * 스토어(`addBlock`)와 선택기(`BlockTypeSelector`)가 같이 참조한다 — 한쪽만 늘리면
 * 유실이 되살아나기 때문이다.
 *
 * 서버가 블록을 저장하게 되면 그때 이 목록을 늘린다.
 */
export const PERSISTED_BLOCK_TYPES = ['link'] as const

export type PersistedBlockType = (typeof PERSISTED_BLOCK_TYPES)[number]

/** 이 타입을 지금 저장할 수 있는가. */
export function isPersistedBlockType(type: BlockType): type is PersistedBlockType {
  return (PERSISTED_BLOCK_TYPES as readonly string[]).includes(type)
}

/**
 * 공개 페이지에 걸 수 있는 링크인가. **`http`/`https` 절대 URL만 허용한다.**
 *
 * ## 왜 필요한가
 *
 * 백엔드는 `url: String` 을 그대로 받아 저장한다 — 검증이 전혀 없다. 그래서 새 블록의
 * 기본값이던 `'https://'` 같은 **프로토콜만 있는 문자열도 실제 링크처럼 저장**됐고,
 * 공개 페이지는 그것을 `<a href>` 로 그려 방문자를 아무 데도 아닌 곳으로 보냈다.
 *
 * ## 왜 http/https 만인가
 *
 * 이 값은 남이 클릭하는 공개 페이지의 `href` 가 된다. `javascript:` 같은 스킴이 들어가면
 * 링크가 아니라 실행이 된다. 상대 경로도 방문자 기준으로 해석돼 우리 도메인으로
 * 되돌아온다 — 크리에이터가 의도한 외부 링크가 아니다.
 */
export function isValidLinkUrl(url: string | null | undefined): boolean {
  if (!url) return false
  const trimmed = url.trim()
  if (trimmed.length === 0) return false
  let parsed: URL
  try {
    parsed = new URL(trimmed)
  } catch {
    return false
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false
  // `https://` 처럼 호스트가 없는 값은 파싱은 되지만 가리키는 곳이 없다.
  return parsed.hostname.length > 0
}
export type ThemeStyle = 'minimal' | 'rounded' | 'gradient' | 'dark' | 'colorful'

export interface LinkBlock {
  id: number
  type: 'link'
  title: string
  url: string
  icon?: string
  isVisible: boolean
  clickCount: number
}

export interface HeaderBlock {
  id: number
  type: 'header'
  text: string
  isVisible: boolean
}

export interface SocialBlock {
  id: number
  type: 'social'
  platform: string
  url: string
  isVisible: boolean
}

export interface VideoBlock {
  id: number
  type: 'video'
  title: string
  /**
   * 영상 링크. **아직 입력하지 않았으면 빈 문자열.**
   *
   * 예전에는 새 블록을 `'https://'` 로 시작했다. 값이 채워진 것처럼 보이지만 어떤
   * 영상도 가리키지 않고, 미리보기의 `<a href>` 에 들어가면 현재 페이지로 이동한다.
   * 입력 필드가 `v-model` 로 문자열을 다루므로 빈 문자열이 "미입력" 이다.
   */
  videoUrl: string
  /**
   * 썸네일 이미지 URL. **지정 전에는 `null`** — 지어내지 않는다.
   *
   * 예전에는 외부 랜덤 이미지 서비스 URL을 초기값으로 넣어, 사용자가 영상을 지정하기도
   * 전에 **실제 영상 썸네일처럼 보이는 남의 사진**이 미리보기에 떴다.
   *
   * `null` 이면 화면이 로컬 placeholder 를 그린다 — `src=''` 로 두지 않는다
   * (브라우저가 현재 페이지를 다시 요청한다).
   */
  thumbnailUrl: string | null
  isVisible: boolean
}

export interface DividerBlock {
  id: number
  type: 'divider'
  isVisible: boolean
}

export interface TextBlock {
  id: number
  type: 'text'
  content: string
  isVisible: boolean
}

export type BioBlock = LinkBlock | HeaderBlock | SocialBlock | VideoBlock | DividerBlock | TextBlock

export interface BioPage {
  id: string
  username: string
  displayName: string
  bio: string
  avatarUrl: string
  theme: ThemeStyle
  backgroundColor: string
  textColor: string
  buttonColor: string
  buttonTextColor: string
  blocks: BioBlock[]
  totalViews: number
  totalClicks: number
  createdAt: string
  updatedAt: string
}
