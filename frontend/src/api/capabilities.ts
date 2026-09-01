import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface AppCapability {
  key: string
  enabled: boolean
  reason?: string | null
}

/**
 * 캐시 유효 기간.
 *
 * 예전에는 **무기한**이었다. 한 번 받아 두면 탭을 닫기 전까지 다시 묻지 않으므로, 운영자가
 * 결제 설정을 켠 뒤에도 이미 열려 있던 탭은 계속 "결제 사용 불가"를 보여줬다. 사용자는
 * 설정이 켜졌다는 사실을 알 방법이 없고, 새로고침해야 한다는 것도 모른다.
 *
 * 반대로 매번 물으면 라우팅 한 번에 요청 하나가 붙는다. 이 값은 라우팅 연타에는 캐시가
 * 듣고, 운영 설정 변경은 1 분 안에 저절로 반영되는 지점이다. 그보다 빨리 확인해야 하면
 * `list({ force: true })` 로 명시적으로 재검증한다.
 */
const CAPABILITY_TTL_MS = 60_000

interface Pending {
  /** 이 요청의 번호. 아래 세대 비교와 슬롯 정리에 쓴다. */
  gen: number
  promise: Promise<AppCapability[]>
}

let cachedCapabilities: AppCapability[] | null = null
let cachedAt = 0

/**
 * 요청에 붙이는 번호. **늦게 출발한 요청이 더 새로운 서버 상태를 본다.**
 *
 * 일반 조회와 강제 재검증이 동시에 떠 있을 수 있고, 응답 순서는 보장되지 않는다. 번호가
 * 없으면 **먼저 출발한 요청의 늦은 응답이 재검증 결과를 덮어쓴다** — 운영자가 결제를 켠
 * 직후 사용자가 재확인을 눌렀는데, 설정 변경 전에 떠난 응답이 나중에 도착해 "사용 불가"로
 * 되돌려 놓는 상황이다. 눈에는 재확인이 실패한 것처럼 보이고 원인은 어디에도 남지 않는다.
 */
let generation = 0
/** 지금 캐시에 들어 있는 응답의 요청 번호. 이보다 오래된 응답은 캐시를 건드리지 못한다. */
let cachedGeneration = 0

/**
 * 캐시를 채울 자격이 있는 **최소 요청 번호.**
 *
 * 강제 재검증이 시작되는 순간, 그보다 **먼저 출발한 요청은 전부 자격을 잃는다.** 재검증은
 * "지금 캐시를 믿지 못하겠다"는 선언이고, 그때 이미 날아가 있던 응답은 그 선언이 가리키는
 * 옛 상태를 담고 있기 때문이다.
 *
 * 번호 비교(`cachedGeneration`)만으로는 부족하다. 그것은 **성공한 응답끼리의 순서**만
 * 가린다. 재검증이 **실패하면** `cachedGeneration` 이 오르지 않으므로, 먼저 출발한 일반
 * 응답이 뒤늦게 도착해 캐시를 채우고 `cachedAt` 까지 60 초 늘린다 — 확인하지 못한 상태가
 * **확인된 것처럼** 굳어지고, 실패한 재확인을 우회한다.
 *
 * 그래서 이 장벽은 재검증이 **끝날 때가 아니라 시작할 때** 올라간다. 실패하든 성공하든
 * 결과와 무관하다.
 */
let minCacheableGeneration = 0

/**
 * 진행 중인 요청을 **일반과 강제로 나눠** 들고 있다.
 *
 * 한 칸만 두면 강제 요청이 일반 요청을 덮어써 추적을 잃는다. 그러면 먼저 끝난 일반 요청의
 * 뒷정리가 **진행 중인 강제 요청의 자리를 지워**, 뒤이은 재확인이 합류하지 못하고 요청을
 * 하나 더 만든다.
 */
let pendingNormal: Pending | null = null
let pendingForced: Pending | null = null

function isFresh(): boolean {
  return cachedCapabilities !== null && Date.now() - cachedAt < CAPABILITY_TTL_MS
}

function start(force: boolean): Promise<AppCapability[]> {
  const gen = ++generation

  const promise = apiClient.get<ResData<AppCapability[]>>('/capabilities')
    .then(unwrapResponse)
    .then((items) => {
      /*
       * **더 새로운 응답이 이미 캐시에 있으면 덮지 않는다.**
       *
       * 실패 시에는 여기까지 오지 않으므로 `cachedAt` 도 갱신되지 않는다 — 오래된 값이
       * 신선한 것으로 되살아나지 않고, 다음 호출이 다시 묻는다.
       */
      if (gen >= minCacheableGeneration && gen > cachedGeneration) {
        cachedCapabilities = items
        cachedAt = Date.now()
        cachedGeneration = gen
      }
      return items
    })
    .finally(() => {
      // 자기 자리만 비운다. 번호로 확인하지 않으면 남의 진행 중인 요청을 지운다.
      if (pendingNormal?.gen === gen) pendingNormal = null
      if (pendingForced?.gen === gen) pendingForced = null
    })

  const entry: Pending = { gen, promise }
  if (force) {
    /*
     * 이 시점 이전에 출발한 요청은 캐시를 채울 자격을 잃는다. 진행 중인 일반 요청도
     * 자리에서 뗀다 — 캐시에 넣지 못할 응답을 **새 호출자에게 물려줄** 이유가 없다.
     * 원래 호출자는 자기 promise 를 이미 들고 있으므로 답을 그대로 받는다.
     */
    minCacheableGeneration = gen
    pendingNormal = null
    pendingForced = entry
  }
  else {
    pendingNormal = entry
  }
  return promise
}

export const capabilitiesApi = {
  /**
   * 서버가 정한 기능 활성 목록.
   *
   *  - 캐시가 **신선하면** 그대로 준다(라우팅 연타에 요청이 붙지 않는다).
   *  - 오래됐으면 다시 묻는다. 실패하면 예외가 그대로 올라가고 캐시는 갱신되지 않는다 —
   *    다음 호출도 다시 묻는다. **오래된 값을 신선한 척 돌려주지 않는다.**
   *  - `force: true` 는 명시적 재검증이다. 진행 중인 **강제** 요청에만 합류해 연타가 요청을
   *    늘리지 않게 한다. 진행 중인 일반 요청에는 합류하지 않는다 — 그것은 설정이 바뀌기
   *    전에 출발했을 수 있어 재검증이 되지 못한다.
   */
  list(options?: { force?: boolean }): Promise<AppCapability[]> {
    if (options?.force === true) {
      return pendingForced?.promise ?? start(true)
    }

    /*
     * **재검증이 떠 있으면 캐시보다 먼저다.**
     *
     * 신선함을 먼저 보면, 누군가 재확인을 누른 **직후** 다른 화면이 물었을 때 아직 유효한
     * 옛 캐시가 즉시 나간다. 곧 도착할 재검증 결과와 다른 값이라 **같은 순간 두 화면이
     * 서로 다른 결제 상태를 보게 된다** — 한쪽은 결제를 열고 다른 쪽은 막는다.
     *
     * 기다리는 비용은 이미 떠 있는 요청 하나뿐이고, 요청이 늘지도 않는다.
     */
    if (pendingForced) return pendingForced.promise

    if (isFresh()) return Promise.resolve(cachedCapabilities as AppCapability[])
    return pendingNormal?.promise ?? start(false)
  },

  /**
   * 캐시를 버린다. feature-unavailable 복구 흐름이 쓰는 경로다.
   *
   * 이미 떠 있는 요청의 응답도 **버린 시점보다 오래된 것으로 본다.** 그러지 않으면 방금
   * 버린 값과 같은 응답이 곧바로 캐시를 다시 채워, 무효화한 적이 없는 것과 같아진다.
   */
  clearCache() {
    cachedCapabilities = null
    cachedAt = 0
    // 캐시가 비었으므로 "무엇이 들어 있는지"도 없다. 자격은 아래 장벽 하나로만 판단한다.
    cachedGeneration = 0
    // 이미 떠 있는 요청은 전부 무효화 이전의 상태를 담고 있다. 캐시를 채우지 못한다.
    minCacheableGeneration = generation + 1
    /*
     * 자리에서도 뗀다. 그러지 않으면 무효화 **직후의 조회가 무효화된 요청에 합류해**
     * 방금 버린 것과 같은 답을 받는다 — 버린 적이 없는 것과 같다. 기존 호출자는 자기
     * promise 를 들고 있으므로 답을 그대로 받는다.
     */
    pendingNormal = null
    pendingForced = null
  },
}
