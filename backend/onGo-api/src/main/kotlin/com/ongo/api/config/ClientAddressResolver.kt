package com.ongo.api.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * 요청을 보낸 클라이언트의 주소를 **위조할 수 없는 범위 안에서만** 판정한다.
 *
 * ## 왜 필요한가
 *
 * 이 애플리케이션은 nginx 뒤에서 돈다(`proxy_pass http://127.0.0.1:8070`). 그래서
 * `HttpServletRequest.remoteAddr` 은 **모든 요청에 대해 `127.0.0.1`** 이다. 이 값을 상한 키로
 * 쓰면 버킷이 사실상 하나뿐이라, 공격자 한 명이 그 버킷을 비우는 것만으로 **정상 사용자 전원의
 * 로그인과 토큰 갱신을 막을 수 있다.** 상한이 공격자를 막는 대신 서비스를 막는다.
 *
 * ## 무엇을 믿는가
 *
 * nginx 는 두 헤더를 채워서 넘긴다.
 *
 *  - `X-Real-IP $remote_addr` — 클라이언트가 보낸 같은 이름의 헤더를 **덮어쓴다.** 그래서 이
 *    값은 nginx 가 본 실제 피어이고 위조가 남지 않는다.
 *  - `X-Forwarded-For $proxy_add_x_forwarded_for` — 기존 값 **뒤에 덧붙인다.** 앞쪽 항목은
 *    전부 클라이언트가 보낸 것이고, **마지막 항목만** nginx 가 쓴 값이다.
 *
 * ## 무엇을 믿지 않는가 — 이 클래스의 핵심
 *
 * 헤더는 프록시가 덮어써 주기 전에는 그냥 **사용자 입력**이다. 그래서 `remoteAddr` 이
 * 루프백일 때, 즉 **nginx 가 직접 연결한 요청일 때만** 헤더를 본다. 그 밖의 경우에는 헤더를
 * 전부 무시하고 피어 주소를 쓴다.
 *
 * 이 조건이 2차 방어선이다. 백엔드는 지금 `*:8070` 으로 열려 있고 외부 차단은 방화벽 규칙
 * 하나에만 의존한다. 방화벽이 꺼지거나 규칙이 바뀌어 8070 에 직접 접속할 수 있게 되더라도,
 * 그때 피어는 루프백이 아니므로 위조한 `X-Real-IP` 가 먹히지 않는다.
 *
 * ## 주소를 다루는 규칙
 *
 *  - **DNS 를 조회하지 않는다.** `InetAddress.getByName` 은 리터럴이 아닌 입력에 이름 해석을
 *    시도한다. 헤더는 사용자 입력이므로 그것만으로 외부 조회를 유발하는 통로가 된다. 그래서
 *    리터럴 파서를 직접 둔다. (`InetAddress.ofLiteral` 은 JDK 22+ 라 이 프로젝트(JDK 21)에서는
 *    쓸 수 없다.)
 *  - **리터럴로 파싱되지 않는 값은 쓰지 않는다.** 아무 문자열이나 키로 받으면 위조 헤더마다
 *    새 버킷이 생겨 상한이 그대로 뚫린다.
 *  - **표기를 정규화한다.** `::1`·`0:0:0:0:0:0:0:1`·`[::1]:443`·`::ffff:127.0.0.1` 이 서로 다른
 *    키가 되면 같은 클라이언트가 버킷을 여러 개 갖게 되어 상한이 무의미해진다.
 *  - **주소를 로그에 남기거나 저장하지 않는다.** 반환값은 상한 버킷의 키로만 쓰인다.
 */
@Component
class ClientAddressResolver {

    /** 서블릿 요청에서 판정한다. 헤더 이름을 아는 유일한 자리다. */
    fun resolve(request: HttpServletRequest): String = resolve(
        remoteAddr = request.remoteAddr,
        realIpHeader = request.getHeader(REAL_IP_HEADER),
        forwardedForHeader = request.getHeader(FORWARDED_FOR_HEADER),
    )

    /**
     * 판정 규칙 본체. 서블릿 없이도 검증할 수 있도록 값만 받는다.
     *
     * 우선순위는 `X-Real-IP` → `X-Forwarded-For` 마지막 항목 → 피어 주소이며, **피어가
     * 루프백일 때에 한해서만** 앞의 두 가지를 본다.
     */
    fun resolve(remoteAddr: String?, realIpHeader: String?, forwardedForHeader: String?): String {
        val peer = canonicalOrNull(remoteAddr) ?: return UNRESOLVED_PEER

        val client = if (isLoopback(peer)) {
            canonicalOrNull(realIpHeader) ?: lastForwardedHop(forwardedForHeader) ?: peer
        } else {
            peer
        }
        return bucketKey(client)
    }

    /**
     * 상한 버킷의 키. **IPv6 는 /64 프리픽스로 묶는다.**
     *
     * IPv4 주소 하나는 한 가입자를 뜻하지만 IPv6 는 그렇지 않다. ISP 는 가입자에게 /64 이상을
     * 위임하고, 프라이버시 확장(RFC 4941, Windows·macOS·iOS·Android 기본 활성)은 그 안에서
     * 인터페이스 주소를 계속 바꾼다. 주소 하나(/128)를 키로 쓰면 **한 사용자가 2^64 개의 키를
     * 마음대로 만들 수 있어** 상한이 IPv6 클라이언트에게만 통째로 무효가 된다.
     *
     * /64 는 "하나의 LAN" 단위이므로 IPv4 주소 하나와 대응하는 granularity 다. 더 넓게 잡으면
     * (예: /48) 서로 다른 가입자가 한 버킷을 공유하게 되어, 이번에 없앤 전역 버킷 문제를
     * 작은 규모로 다시 만드는 셈이 된다.
     *
     * 판정에 쓰는 [isLoopback] 은 이 축약 **전의** 온전한 주소를 본다. `::1` 을 /64 로 줄이면
     * `::/64` 안의 모든 주소와 구별되지 않아 신뢰 프록시 판정이 무너진다.
     */
    private fun bucketKey(canonical: String): String {
        if (':' !in canonical) return canonical
        return canonical.split(':').take(IPV6_PREFIX_GROUPS).joinToString(":") + "::"
    }

    /**
     * `X-Forwarded-For` 에서 **마지막 항목만** 읽는다.
     *
     * 파싱에 실패해도 앞 항목으로 거슬러 올라가지 않는다. 앞 항목은 클라이언트가 보낸 값이라,
     * 거슬러 올라가는 순간 우리가 직접 우회 경로를 열어주는 셈이 된다. 읽을 수 없으면 없는
     * 것으로 보고 피어 주소로 되돌아간다.
     */
    private fun lastForwardedHop(header: String?): String? {
        val entries = header?.split(',') ?: return null
        return canonicalOrNull(entries.lastOrNull())
    }

    /** 루프백인가. [canonicalOrNull] 을 통과한 정규 표기만 들어온다. */
    private fun isLoopback(canonical: String): Boolean =
        canonical == IPV6_LOOPBACK || canonical.startsWith(IPV4_LOOPBACK_PREFIX)

    /**
     * IP 리터럴이면 정규 표기로, 아니면 null.
     *
     * 대괄호·포트·스코프 ID 를 걷어낸 뒤 IPv4 → IPv6 순으로 시도한다. 어느 쪽으로도 읽히지
     * 않으면 **버린다.** 주소가 아닌 값을 키로 삼지 않는 것이 이 클래스의 안전성 근거다.
     */
    private fun canonicalOrNull(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.length > MAX_LITERAL_LENGTH) return null

        val bare = stripBracketsAndPort(text) ?: return null
        // 스코프 ID(`fe80::1%eth0`)는 보낸 쪽 인터페이스 이름이라 원격 주소에서는 의미가 없다.
        val zoneless = bare.substringBefore('%').takeIf { it.isNotEmpty() } ?: return null

        parseIpv4(zoneless)?.let { return it.joinToString(".") }
        parseIpv6(zoneless)?.let { return canonicalIpv6(it) }
        return null
    }

    /**
     * `[::1]:443` / `1.2.3.4:8080` 같은 표기에서 주소만 남긴다.
     *
     * 대괄호가 없을 때는 콜론이 **정확히 하나**일 때만 포트로 본다. IPv6 는 콜론이 둘 이상이라
     * `::1` 을 포트로 오해하지 않는다.
     */
    private fun stripBracketsAndPort(text: String): String? {
        if (text.startsWith("[")) {
            val close = text.indexOf(']')
            if (close <= 1) return null
            val rest = text.substring(close + 1)
            if (rest.isNotEmpty() && !rest.startsWith(":")) return null
            return text.substring(1, close)
        }
        val colon = text.indexOf(':')
        if (colon > 0 && text.indexOf(':', colon + 1) < 0) {
            return text.substring(0, colon)
        }
        return text
    }

    /**
     * 점 표기 IPv4. 네 개의 10진 옥텟만 받는다.
     *
     * 선행 0(`010`)은 거부한다. 8진수로 읽는 구현이 있어 같은 주소가 다른 값으로 해석될 수
     * 있고, 그 모호함은 곧 같은 클라이언트가 두 개의 키를 갖는다는 뜻이다.
     */
    private fun parseIpv4(text: String): IntArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null

        val octets = IntArray(4)
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part[0] == '0') return null
            for (ch in part) if (ch !in '0'..'9') return null
            val value = part.toInt()
            if (value > 255) return null
            octets[index] = value
        }
        return octets
    }

    /** IPv6 리터럴을 16비트 그룹 8개로. 읽을 수 없으면 null. */
    private fun parseIpv6(text: String): IntArray? {
        if (':' !in text) return null

        val compression = text.indexOf("::")
        // `::` 가 두 번 나오면 어느 쪽이 몇 그룹인지 정해지지 않는다.
        if (compression >= 0 && text.indexOf("::", compression + 2) >= 0) return null

        val headText = if (compression >= 0) text.substring(0, compression) else text
        val tailText = if (compression >= 0) text.substring(compression + 2) else null

        val head = expandGroups(headText, allowEmbeddedIpv4 = tailText == null) ?: return null
        val tail = if (tailText == null) EMPTY_GROUPS
        else expandGroups(tailText, allowEmbeddedIpv4 = true) ?: return null

        if (tailText == null) return if (head.size == GROUP_COUNT) head else null

        // `::` 는 최소 한 그룹의 0 을 대신한다. 양쪽 합이 8이면 압축할 것이 없다는 뜻이다.
        if (head.size + tail.size >= GROUP_COUNT) return null

        val groups = IntArray(GROUP_COUNT)
        head.copyInto(groups, 0)
        tail.copyInto(groups, GROUP_COUNT - tail.size)
        return groups
    }

    /**
     * 콜론으로 나뉜 그룹들을 16비트 값으로 편다.
     *
     * 점 표기 IPv4 꼬리(`::ffff:1.2.3.4`)는 **주소의 맨 끝에서만** 허용하며 그룹 두 개로 센다.
     */
    private fun expandGroups(text: String, allowEmbeddedIpv4: Boolean): IntArray? {
        if (text.isEmpty()) return EMPTY_GROUPS

        val tokens = text.split(':')
        val groups = ArrayList<Int>(GROUP_COUNT)
        for (index in tokens.indices) {
            val token = tokens[index]
            // 빈 토큰은 `:::` 이나 한쪽에만 붙은 단일 콜론이다.
            if (token.isEmpty()) return null

            if ('.' in token) {
                if (!allowEmbeddedIpv4 || index != tokens.lastIndex) return null
                val octets = parseIpv4(token) ?: return null
                groups += (octets[0] shl 8) or octets[1]
                groups += (octets[2] shl 8) or octets[3]
                continue
            }

            if (token.length > 4) return null
            // `toIntOrNull(16)` 은 부호를 받아들인다. 16진 숫자만 있는지 직접 확인한다.
            for (ch in token) {
                val isHex = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
                if (!isHex) return null
            }
            groups += token.toInt(16)
        }

        if (groups.size > GROUP_COUNT) return null
        return groups.toIntArray()
    }

    /**
     * 그룹 8개를 하나의 표기로 모은다.
     *
     * IPv4-mapped(`::ffff:a.b.c.d`)는 실제로 그 IPv4 주소다. 듀얼 스택 환경에서 같은
     * 클라이언트가 표기만 달라 버킷을 두 개 갖지 않도록 IPv4 표기로 되돌린다.
     */
    private fun canonicalIpv6(groups: IntArray): String {
        val isIpv4Mapped = groups[0] == 0 && groups[1] == 0 && groups[2] == 0 &&
            groups[3] == 0 && groups[4] == 0 && groups[5] == 0xffff
        if (isIpv4Mapped) {
            return "${groups[6] ushr 8}.${groups[6] and 0xff}.${groups[7] ushr 8}.${groups[7] and 0xff}"
        }
        return groups.joinToString(":") { it.toString(16) }
    }

    companion object {
        const val REAL_IP_HEADER = "X-Real-IP"
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"

        /**
         * 피어 주소를 읽을 수 없을 때 쓰는 키.
         *
         * 서블릿 컨테이너는 항상 리터럴을 주므로 실제 요청에서는 나오지 않는다. 그래도 값을
         * 그대로 키로 쓰지는 않는다 — 읽을 수 없는 값들을 각각 다른 키로 취급하면 상한이
         * 무의미해지므로, 구분할 수 없는 것은 **하나로 묶어** 함께 제한한다.
         */
        const val UNRESOLVED_PEER = "unresolved"

        private const val IPV6_LOOPBACK = "0:0:0:0:0:0:0:1"
        private const val IPV4_LOOPBACK_PREFIX = "127."

        /** `0000:...:255.255.255.255` 가 45자다. 대괄호·포트·스코프까지 넉넉히 잡는다. */
        private const val MAX_LITERAL_LENGTH = 64
        private const val GROUP_COUNT = 8

        /** IPv6 버킷을 묶는 단위. 4그룹 = /64 = LAN 하나. */
        private const val IPV6_PREFIX_GROUPS = 4
        private val EMPTY_GROUPS = IntArray(0)
    }
}
