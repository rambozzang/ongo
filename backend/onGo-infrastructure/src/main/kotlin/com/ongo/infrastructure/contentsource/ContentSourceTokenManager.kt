package com.ongo.infrastructure.contentsource

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.contentsource.ContentSource
import com.ongo.domain.contentsource.ContentSourceRepository
import com.ongo.domain.contentsource.ContentSourceStatus
import com.ongo.domain.contentsource.exception.ContentSourceExpiredException
import com.ongo.domain.contentsource.exception.ContentSourceNotConnectedException
import com.ongo.infrastructure.external.googledrive.GoogleDriveOAuthClient
import com.ongo.infrastructure.external.googledrive.OAuthTokenExchangeException
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * ContentSource(Google Drive 등) OAuth access token 수명 관리.
 *
 * - 만료된 토큰은 자동 refresh
 * - per-source-id 락으로 동시 refresh 경합 제어
 * - refresh 실패 시 status=EXPIRED 마킹 + ContentSourceExpiredException
 *
 * Note: infrastructure 모듈에 위치한 이유는 GoogleDriveOAuthClient(infra)에 직접 의존하고
 * 순환 의존(infra→app 기존, app→infra 신규) 을 피하기 위함이다.
 */
@Component
class ContentSourceTokenManager(
    private val repo: ContentSourceRepository,
    private val oauth: GoogleDriveOAuthClient,
    private val encryptor: TokenEncryptionPort,
) {
    private val locks: LoadingCache<Long, ReentrantLock> = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .build { ReentrantLock() }

    fun ensureValidToken(sourceId: Long): String {
        val lock = locks.get(sourceId)!!
        lock.lock()
        try {
            val source = repo.findById(sourceId) ?: throw ContentSourceNotConnectedException()
            if (source.status != ContentSourceStatus.ACTIVE) {
                throw ContentSourceExpiredException(source.lastError ?: source.status.name)
            }
            if (!source.needsRefresh(Instant.now())) {
                return encryptor.decrypt(source.accessTokenEncrypted)
            }
            return refreshLocked(source)
        } finally {
            lock.unlock()
        }
    }

    private fun refreshLocked(source: ContentSource): String {
        val refreshToken = source.refreshTokenEncrypted?.let(encryptor::decrypt)
            ?: run {
                repo.updateStatus(source.id, ContentSourceStatus.EXPIRED, "missing refresh_token")
                throw ContentSourceExpiredException("missing refresh_token")
            }
        try {
            val resp = oauth.refresh(refreshToken)
            val newAccessEnc = encryptor.encrypt(resp.accessToken)
            val newRefreshEnc = resp.refreshToken?.let(encryptor::encrypt)
            val newExpiresAt = Instant.now().plusSeconds(resp.expiresIn.toLong())
            repo.updateTokens(source.id, newAccessEnc, newRefreshEnc, newExpiresAt)
            return resp.accessToken
        } catch (e: OAuthTokenExchangeException) {
            repo.updateStatus(source.id, ContentSourceStatus.EXPIRED, e.message)
            throw ContentSourceExpiredException(e.message ?: "refresh failed")
        }
    }
}
