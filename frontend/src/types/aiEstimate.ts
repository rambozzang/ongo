/**
 * 서버가 추정값에 붙여 보내는 표시. 백엔드 `AiEstimate` 와 같다.
 * 이 값이 붙은 숫자·등급은 `AiEstimateBadge` 와 함께 보여야 한다.
 */
export interface AiEstimate {
  estimated: boolean
  /** 무엇을 보고, 무엇을 보지 않고 추정했는지 */
  basis: string
}
