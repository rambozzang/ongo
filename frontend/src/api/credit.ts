import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type { CreditBalance, CreditTransaction } from '@/types/credit'

/**
 * 크레딧 조회 전용.
 *
 * 구매는 여기 있지 않다. 예전 `purchase()` 는 `POST /credits/purchase` 를 불렀는데,
 * 그 서버 경로는 PG 를 거치지 않고 크레딧도 지급하지 않으면서 성공을 응답했다.
 * 호출하는 화면이 하나도 없어 실제 피해는 없었지만, 남겨두면 언젠가 누군가 "이미 있는
 * API"라고 믿고 결제 화면에 연결한다.
 *
 * 실제 구매는 `usePortOne().openCreditCheckout(packageName)` 이다 —
 * PortOne 체크아웃을 열고, 완료 후 서버가 PG 에 재조회해 검증한 뒤 지급한다.
 */
export const creditApi = {
  getBalance() {
    return apiClient.get<ResData<CreditBalance>>('/credits').then(unwrapResponse)
  },

  getTransactions(params: PageRequest) {
    return apiClient
      .get<ResData<PageResponse<CreditTransaction>>>('/credits/transactions', { params })
      .then(unwrapResponse)
  },
}
