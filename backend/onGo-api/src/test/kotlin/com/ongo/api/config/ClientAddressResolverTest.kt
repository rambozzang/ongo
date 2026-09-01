package com.ongo.api.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 상한 키가 되는 클라이언트 주소 판정의 계약.
 *
 * ## 이 테스트가 지키는 것
 *
 * 두 가지 실패를 동시에 막아야 하며, 한쪽만 지키면 나머지 하나가 그대로 사고가 된다.
 *
 *  - **너무 좁게 보면** — `remoteAddr` 만 쓰면 nginx 뒤에서 모든 요청이 `127.0.0.1` 이라
 *    버킷이 하나뿐이다. 공격자 한 명이 정상 사용자 전원의 로그인을 막는다.
 *  - **너무 넓게 믿으면** — 헤더를 무조건 신뢰하면 요청마다 다른 값을 넣어 상한을 그냥 지나간다.
 *    상한이 있으나 마나가 된다.
 *
 * 그래서 **피어가 루프백일 때만** 헤더를 보고, 그때도 **위조가 남지 않는 자리**만 읽는다.
 */
class ClientAddressResolverTest {

    private val resolver = ClientAddressResolver()

    private val loopback = "127.0.0.1"
    private val ipv6Loopback = "0:0:0:0:0:0:0:1"

    /* ── 신뢰 프록시 뒤: 헤더를 읽는다 ─────────────────────────────────── */

    @Test
    @DisplayName("서로 다른 클라이언트는 서로 다른 키를 받는다")
    fun distinctClientsGetDistinctKeys() {
        val first = resolver.resolve(loopback, "203.0.113.10", null)
        val second = resolver.resolve(loopback, "203.0.113.11", null)

        assertEquals("203.0.113.10", first)
        assertEquals("203.0.113.11", second)
        // 이 한 줄이 전역 버킷 회귀를 막는다.
        assertNotEquals(first, second)
    }

    @Test
    @DisplayName("루프백 피어에서는 X-Real-IP 를 쓴다")
    fun usesRealIpBehindTheTrustedProxy() {
        assertEquals("203.0.113.10", resolver.resolve(loopback, "203.0.113.10", null))
        assertEquals("203.0.113.10", resolver.resolve(ipv6Loopback, "203.0.113.10", null))
        // 압축 표기 루프백도 같은 판정을 받아야 한다.
        assertEquals("203.0.113.10", resolver.resolve("::1", "203.0.113.10", null))
    }

    @Test
    @DisplayName("X-Real-IP 가 없으면 X-Forwarded-For 의 마지막 hop 을 쓴다")
    fun fallsBackToTheLastForwardedHop() {
        val key = resolver.resolve(loopback, null, "203.0.113.10")

        assertEquals("203.0.113.10", key)
    }

    /**
     * **첫 hop 조작으로 우회할 수 없다.**
     *
     * nginx 는 `$proxy_add_x_forwarded_for` 로 기존 값 **뒤에 덧붙인다.** 그래서 앞쪽 항목은
     * 전부 클라이언트가 직접 써 넣은 값이다. 앞에서부터 읽으면 요청마다 다른 값을 넣어
     * 무제한으로 새 버킷을 만들 수 있다.
     */
    @Test
    @DisplayName("XFF 앞 hop 을 조작해도 키가 달라지지 않는다")
    fun forgedLeadingHopsCannotChangeTheKey() {
        val real = "203.0.113.10"
        val forged = listOf(
            "1.1.1.1, $real",
            "9.9.9.9, 8.8.8.8, $real",
            "evil, $real",
            "::1, $real",
        )

        val keys = forged.map { resolver.resolve(loopback, null, it) }.toSet()

        assertEquals(setOf(real), keys)
    }

    @Test
    @DisplayName("X-Real-IP 가 X-Forwarded-For 보다 우선한다")
    fun realIpWinsOverForwardedFor() {
        val key = resolver.resolve(loopback, "203.0.113.10", "1.1.1.1, 203.0.113.99")

        assertEquals("203.0.113.10", key)
    }

    /* ── 신뢰하지 않는 피어: 헤더를 전부 무시한다 ───────────────────────── */

    /**
     * **8070 직접 접속으로 우회할 수 없다.**
     *
     * 백엔드는 지금 `*:8070` 으로 열려 있고 외부 차단은 방화벽 규칙 하나에 의존한다. 그 규칙이
     * 사라져도 위조 헤더가 먹히면 안 된다 — 피어가 루프백이 아니면 헤더는 읽지 않는다.
     */
    @Test
    @DisplayName("피어가 루프백이 아니면 forwarding 헤더를 전부 무시한다")
    fun ignoresForwardingHeadersFromAnUntrustedPeer() {
        val attacker = "203.0.113.50"

        assertEquals(attacker, resolver.resolve(attacker, "1.1.1.1", null))
        assertEquals(attacker, resolver.resolve(attacker, null, "1.1.1.1"))
        assertEquals(attacker, resolver.resolve(attacker, "1.1.1.1", "2.2.2.2, 3.3.3.3"))
    }

    @Test
    @DisplayName("비신뢰 피어는 헤더를 바꿔도 같은 키를 받는다")
    fun anUntrustedPeerCannotMultiplyItsBuckets() {
        val attacker = "203.0.113.50"

        val keys = (1..20).map { resolver.resolve(attacker, "10.0.0.$it", "10.1.0.$it") }.toSet()

        assertEquals(setOf(attacker), keys)
    }

    /* ── 이상한 헤더는 우회 통로가 되지 않는다 ──────────────────────────── */

    /**
     * **마지막 hop 이 읽히지 않으면 앞으로 거슬러 올라가지 않는다.**
     *
     * 거슬러 올라가면 바로 클라이언트가 써 넣은 값을 키로 쓰게 된다. 읽을 수 없으면 없는
     * 것으로 보고 피어 주소로 돌아가는 편이 안전하다.
     */
    @Test
    @DisplayName("XFF 마지막 hop 이 주소가 아니면 앞 hop 으로 내려가지 않는다")
    fun doesNotWalkBackWhenTheLastHopIsUnreadable() {
        val key = resolver.resolve(loopback, null, "203.0.113.99, not-an-ip")

        assertEquals(loopback, key)
        assertNotEquals("203.0.113.99", key)
    }

    @Test
    @DisplayName("주소가 아닌 값은 키로 쓰지 않는다")
    fun rejectsValuesThatAreNotAddresses() {
        val garbage = listOf(
            "not-an-ip",
            "1.2.3",
            "1.2.3.4.5",
            "256.1.1.1",
            "010.1.1.1",
            "::1::2",
            "12345::",
            "xyz::1",
            "-1::",
            "1:2:3:4:5:6:7:8:9",
            "1:2:3:4:5:6:7",
            "localhost",
            "example.com",
        )

        for (value in garbage) {
            assertEquals(loopback, resolver.resolve(loopback, value, null), "값: $value")
        }
    }

    @Test
    @DisplayName("빈 헤더·공백·빈 항목은 없는 것으로 본다")
    fun treatsBlankHeadersAsAbsent() {
        assertEquals(loopback, resolver.resolve(loopback, "", null))
        assertEquals(loopback, resolver.resolve(loopback, "   ", null))
        assertEquals(loopback, resolver.resolve(loopback, null, ""))
        assertEquals(loopback, resolver.resolve(loopback, null, ",,"))
        assertEquals(loopback, resolver.resolve(loopback, null, null))
    }

    /** 아주 긴 값으로 버킷을 늘리거나 파서를 흔들 수 없어야 한다. */
    @Test
    @DisplayName("비정상적으로 긴 값은 거부한다")
    fun rejectsOverlongValues() {
        assertEquals(loopback, resolver.resolve(loopback, "1".repeat(500), null))
        assertEquals(loopback, resolver.resolve(loopback, "1:".repeat(200) + "1", null))
    }

    /* ── 표기 정규화: 같은 주소는 같은 키여야 한다 ──────────────────────── */

    /**
     * 표기가 갈리면 같은 클라이언트가 버킷을 여러 개 갖는다 — 상한이 그만큼 느슨해진다.
     */
    @Test
    @DisplayName("같은 IPv6 주소는 표기가 달라도 하나의 키가 된다")
    fun normalizesEquivalentIpv6Forms() {
        val forms = listOf(
            "2001:db8::1",
            "2001:0db8:0000:0000:0000:0000:0000:0001",
            "2001:DB8::1",
            "[2001:db8::1]",
            "[2001:db8::1]:443",
            "2001:db8::1%eth0",
        )

        val keys = forms.map { resolver.resolve(loopback, it, null) }.toSet()

        assertEquals(1, keys.size, "표기별로 키가 갈렸다: $keys")
        assertEquals("2001:db8:0:0::", keys.single())
    }

    /* ── IPv6 는 /64 로 묶는다 ─────────────────────────────────────────── */

    /**
     * **주소 하나(/128)를 키로 쓰면 IPv6 클라이언트에게는 상한이 없는 것과 같다.**
     *
     * nginx 는 `listen [::]:443` 으로 IPv6 를 받는다. ISP 는 가입자에게 /64 이상을 위임하고
     * 프라이버시 확장(RFC 4941)은 그 안에서 주소를 계속 바꾼다. 그래서 한 사용자가
     * 2^64 개의 키를 자유롭게 만들 수 있고, IPv4 사용자에게만 적용되는 반쪽짜리 상한이 된다.
     */
    @Test
    @DisplayName("같은 /64 안에서 주소를 바꿔도 하나의 키를 공유한다")
    fun addressesInTheSamePrefixShareOneKey() {
        val sameSubnet = listOf(
            "2001:db8:1:2::1",
            "2001:db8:1:2::dead",
            "2001:db8:1:2:aaaa:bbbb:cccc:dddd",
            "2001:db8:1:2:0:0:0:beef",
            "2001:db8:1:2:ffff:ffff:ffff:ffff",
        )

        val keys = sameSubnet.map { resolver.resolve(loopback, it, null) }.toSet()

        assertEquals(setOf("2001:db8:1:2::"), keys, "같은 /64 가 여러 키로 갈렸다: $keys")
    }

    /** 반대로 **다른 /64 는 반드시 갈라져야** 한다 — 너무 넓게 묶으면 전역 버킷이 작게 되살아난다. */
    @Test
    @DisplayName("다른 /64 는 서로 다른 키를 받는다")
    fun differentPrefixesStayDistinct() {
        val first = resolver.resolve(loopback, "2001:db8:1:2::1", null)
        val second = resolver.resolve(loopback, "2001:db8:1:3::1", null)
        val third = resolver.resolve(loopback, "2001:db8:2:2::1", null)

        assertEquals(3, setOf(first, second, third).size, "다른 /64 가 한 키로 묶였다")
    }

    /** IPv4 는 주소 하나가 가입자 하나이므로 그대로 둔다. */
    @Test
    @DisplayName("IPv4 키는 축약하지 않는다")
    fun ipv4KeysAreNotTruncated() {
        assertEquals("203.0.113.10", resolver.resolve(loopback, "203.0.113.10", null))
        assertEquals("203.0.113.11", resolver.resolve(loopback, "203.0.113.11", null))
    }

    /**
     * **프리픽스 축약이 신뢰 프록시 판정을 삼키면 안 된다.**
     *
     * `::1` 을 먼저 /64 로 줄이면 `::/64` 안의 모든 주소와 구별되지 않는다. 그러면 IPv6 로
     * 직접 접속한 공격자가 루프백으로 오인되어 헤더 위조가 통한다.
     */
    @Test
    @DisplayName("IPv6 루프백 판정은 축약 전 온전한 주소로 한다")
    fun loopbackIsJudgedBeforeTruncation() {
        // 루프백 피어: 헤더를 읽는다.
        assertEquals("203.0.113.10", resolver.resolve("::1", "203.0.113.10", null))

        // ::/64 안에 있지만 루프백이 아닌 주소: 헤더를 읽지 않는다.
        assertEquals("0:0:0:0::", resolver.resolve("::2", "203.0.113.10", null))
        assertEquals("0:0:0:0::", resolver.resolve("::", "203.0.113.10", null))
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 는 해당 IPv4 와 같은 키가 된다")
    fun mapsIpv4MappedFormOntoTheIpv4Key() {
        val mapped = resolver.resolve(loopback, "::ffff:203.0.113.10", null)
        val plain = resolver.resolve(loopback, "203.0.113.10", null)

        assertEquals("203.0.113.10", mapped)
        assertEquals(plain, mapped)
    }

    @Test
    @DisplayName("포트가 붙어 있어도 주소만으로 키를 만든다")
    fun ignoresAnyPortSuffix() {
        assertEquals("203.0.113.10", resolver.resolve(loopback, "203.0.113.10:51234", null))
        assertEquals("203.0.113.10", resolver.resolve(loopback, null, "203.0.113.10:51234"))
    }

    /** 루프백 판정은 `127.0.0.0/8` 전체와 IPv6 루프백을 포함해야 한다. */
    @Test
    @DisplayName("127.0.0.0/8 과 IPv6 루프백을 모두 신뢰 피어로 본다")
    fun recognisesEveryLoopbackForm() {
        val peers = listOf("127.0.0.1", "127.0.0.53", "127.1.2.3", "::1", ipv6Loopback, "::ffff:127.0.0.1")

        for (peer in peers) {
            assertEquals("203.0.113.10", resolver.resolve(peer, "203.0.113.10", null), "피어: $peer")
        }
    }

    /** 루프백이 아닌 사설 대역은 신뢰 프록시가 아니다 — 헤더를 읽지 않는다. */
    @Test
    @DisplayName("사설 대역 피어는 신뢰 프록시가 아니다")
    fun privateRangesAreNotTrustedProxies() {
        assertEquals("10.0.0.5", resolver.resolve("10.0.0.5", "203.0.113.10", null))
        assertEquals("192.168.1.5", resolver.resolve("192.168.1.5", "203.0.113.10", null))
        assertEquals("172.16.0.5", resolver.resolve("172.16.0.5", "203.0.113.10", null))
    }

    /**
     * 피어를 읽을 수 없으면 **구분하지 않고 하나로 묶어** 제한한다.
     *
     * 읽을 수 없는 값을 그대로 키로 쓰면 값마다 새 버킷이 생겨 상한이 무의미해진다.
     */
    @Test
    @DisplayName("피어 주소를 읽을 수 없으면 하나의 키로 묶는다")
    fun groupsUnreadablePeersUnderOneKey() {
        assertEquals(ClientAddressResolver.UNRESOLVED_PEER, resolver.resolve(null, "1.1.1.1", null))
        assertEquals(ClientAddressResolver.UNRESOLVED_PEER, resolver.resolve("", null, null))
        assertEquals(ClientAddressResolver.UNRESOLVED_PEER, resolver.resolve("garbage", "1.1.1.1", null))
    }

    /* ── 서블릿 배선: 실제로 그 헤더를 읽는가 ──────────────────────────── */

    @Test
    @DisplayName("요청에서 X-Real-IP 와 X-Forwarded-For 를 실제로 읽는다")
    fun readsTheHeadersOffTheRequest() {
        val withRealIp = MockHttpServletRequest().apply {
            remoteAddr = loopback
            addHeader(ClientAddressResolver.REAL_IP_HEADER, "203.0.113.10")
            addHeader(ClientAddressResolver.FORWARDED_FOR_HEADER, "1.1.1.1, 203.0.113.99")
        }
        val withForwardedOnly = MockHttpServletRequest().apply {
            remoteAddr = loopback
            addHeader(ClientAddressResolver.FORWARDED_FOR_HEADER, "1.1.1.1, 203.0.113.99")
        }
        val direct = MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.50"
            addHeader(ClientAddressResolver.REAL_IP_HEADER, "1.1.1.1")
        }

        assertEquals("203.0.113.10", resolver.resolve(withRealIp))
        assertEquals("203.0.113.99", resolver.resolve(withForwardedOnly))
        assertEquals("203.0.113.50", resolver.resolve(direct))
    }

    @Test
    @DisplayName("헤더가 없는 요청은 피어 주소를 쓴다")
    fun usesThePeerWhenNoHeadersArePresent() {
        val request = MockHttpServletRequest().apply { remoteAddr = "203.0.113.10" }

        assertEquals("203.0.113.10", resolver.resolve(request))
    }
}
