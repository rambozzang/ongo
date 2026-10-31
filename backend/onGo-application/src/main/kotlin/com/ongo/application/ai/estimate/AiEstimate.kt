package com.ongo.application.ai.estimate

/**
 * "이 값은 AI 추정이다"는 표시와 그 근거.
 *
 * 측정값과 추정값이 같은 모양으로 화면에 나가면 사용자는 둘을 구분할 수 없다. 추정값을 내는
 * 응답은 이것을 함께 실어 화면이 "AI 추정" 배지와 근거 문장을 붙이게 한다.
 *
 * [basis] 는 **무엇을 보고 추정했고 무엇을 보지 않았는지**를 한 문장으로 쓴다
 * (예: "검색량 데이터 없이 AI 모델의 일반 지식으로 추정"). 근거를 말할 수 없는 숫자는 추정으로
 * 표시할 것이 아니라 지워야 한다.
 */
data class AiEstimate(
    val basis: String,
    val estimated: Boolean = true,
)
