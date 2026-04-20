package com.ongo.infrastructure.external.googledrive

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GoogleDriveOAuthClientTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var client: GoogleDriveOAuthClient

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer().apply { start() }
        val props = GoogleDriveProperties(
            clientId = "cid",
            clientSecret = "csec",
            redirectUri = "http://localhost/cb",
        )
        client = GoogleDriveOAuthClient(
            webClient = WebClient.create(),
            props = props,
            tokenEndpoint = mockServer.url("/token").toString(),
            revokeEndpoint = mockServer.url("/revoke").toString(),
        )
    }

    @AfterEach
    fun teardown() { mockServer.shutdown() }

    @Test
    fun `exchangeCode parses successful response`() {
        mockServer.enqueue(
            MockResponse()
                .setBody("""{"access_token":"at","refresh_token":"rt","expires_in":3600,"id_token":"idtok","scope":"drive.readonly","token_type":"Bearer"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val r = client.exchangeCode("authcode")

        assertEquals("at", r.accessToken)
        assertEquals("rt", r.refreshToken)
        assertEquals(3600, r.expiresIn)
        assertEquals("idtok", r.idToken)
    }

    @Test
    fun `exchangeCode throws on 400 response`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"invalid_grant"}""")
                .setHeader("Content-Type", "application/json"),
        )

        assertThrows(OAuthTokenExchangeException::class.java) {
            client.exchangeCode("bad-code")
        }
    }

    @Test
    fun `refresh parses successful response`() {
        mockServer.enqueue(
            MockResponse()
                .setBody("""{"access_token":"new-at","expires_in":3600,"token_type":"Bearer"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val r = client.refresh("rt")

        assertEquals("new-at", r.accessToken)
        assertEquals(null, r.refreshToken) // 갱신 시 refresh_token은 보통 미포함
    }

    @Test
    fun `refresh throws on 400 response`() {
        mockServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertThrows(OAuthTokenExchangeException::class.java) { client.refresh("bad-rt") }
    }

    @Test
    fun `revoke returns true on success`() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
        assertEquals(true, client.revoke("at"))
    }

    @Test
    fun `revoke returns true on 400 - already revoked`() {
        mockServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_token"}"""))
        assertEquals(true, client.revoke("already-revoked-at"))
    }

    @Test
    fun `authUrl builds query string with all params`() {
        val url = client.authUrl(state = "abc-123")
        assert(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assert(url.contains("client_id=cid"))
        assert(url.contains("response_type=code"))
        assert(url.contains("access_type=offline"))
        assert(url.contains("prompt=consent"))
        assert(url.contains("state=abc-123"))
    }
}
