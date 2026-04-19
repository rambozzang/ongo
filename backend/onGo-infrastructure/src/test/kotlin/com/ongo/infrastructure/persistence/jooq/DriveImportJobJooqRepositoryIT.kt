package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.ContentSource
import com.ongo.domain.contentsource.ContentSourceRepository
import com.ongo.domain.contentsource.ContentSourceStatus
import com.ongo.domain.contentsource.ContentSourceType
import com.ongo.domain.contentsource.DriveImportJob
import com.ongo.domain.contentsource.DriveImportJobRepository
import com.ongo.domain.contentsource.DriveImportStatus
import com.ongo.infrastructure.persistence.jooq.Tables.DRIVE_IMPORT_JOBS
import com.ongo.infrastructure.persistence.jooq.Tables.USER_CONTENT_SOURCES
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DriveImportJobJooqRepositoryIT {
    @Autowired lateinit var repo: DriveImportJobRepository
    @Autowired lateinit var contentSourceRepo: ContentSourceRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test"); withUsername("test"); withPassword("test")
        }
        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }
    }

    private var sourceId: Long = 0L
    private var videoIdSeq = 1000L

    @BeforeEach
    fun setup() {
        dsl.deleteFrom(DRIVE_IMPORT_JOBS).execute()
        dsl.deleteFrom(USER_CONTENT_SOURCES).execute()
        dsl.execute("DELETE FROM videos WHERE user_id = 100")
        dsl.execute(
            "INSERT INTO users (id, email, name, provider, provider_id, plan_type) " +
                "VALUES (100, 'u@test.com', 'T', 'GOOGLE', 'g100', 'FREE') ON CONFLICT (id) DO NOTHING"
        )

        sourceId = contentSourceRepo.save(newActiveSource(100L)).id
    }

    @Test
    fun `save and findById roundtrip`() {
        val videoId = insertTestVideo()
        val saved = repo.save(
            newJob(
                userId = 100L, videoId = videoId, driveFileId = "f1",
                size = 1000L, transferred = 0L, status = DriveImportStatus.PENDING,
            ),
        )
        val found = repo.findById(saved.id)!!
        assertEquals(saved.id, found.id)
        assertEquals("f1", found.driveFileId)
        assertEquals(DriveImportStatus.PENDING, found.status)
    }

    @Test
    fun `findActiveByUserAndFileId returns PENDING DOWNLOADING COMPLETED`() {
        val v1 = insertTestVideo()
        val v2 = insertTestVideo()
        val v3 = insertTestVideo()
        repo.save(newJob(100L, v1, "fX", status = DriveImportStatus.DOWNLOADING))
        repo.save(newJob(100L, v2, "fX", status = DriveImportStatus.FAILED))
        repo.save(newJob(100L, v3, "fX", status = DriveImportStatus.COMPLETED))

        val active = repo.findActiveByUserAndFileId(100L, "fX")
        assertEquals(2, active.size) // DOWNLOADING + COMPLETED; FAILED 제외
    }

    @Test
    fun `countActiveByUser counts only PENDING and DOWNLOADING`() {
        val v1 = insertTestVideo()
        val v2 = insertTestVideo()
        val v3 = insertTestVideo()
        repo.save(newJob(100L, v1, "fA", status = DriveImportStatus.PENDING))
        repo.save(newJob(100L, v2, "fB", status = DriveImportStatus.DOWNLOADING))
        repo.save(newJob(100L, v3, "fC", status = DriveImportStatus.COMPLETED))
        assertEquals(2, repo.countActiveByUser(100L))
    }

    @Test
    fun `updateProgress increments bytes_transferred`() {
        val v = insertTestVideo()
        val j = repo.save(
            newJob(
                100L, v, "f", size = 1000L, transferred = 0L,
                status = DriveImportStatus.DOWNLOADING,
            ),
        )
        repo.updateProgress(j.id, 500L)
        assertEquals(500L, repo.findById(j.id)!!.bytesTransferred)
    }

    @Test
    fun `CHECK constraint rejects bytesTransferred greater than fileSizeBytes`() {
        val v = insertTestVideo()
        assertThrows(IntegrityConstraintViolationException::class.java) {
            repo.save(
                newJob(
                    100L, v, "f", size = 100L, transferred = 200L,
                    status = DriveImportStatus.DOWNLOADING,
                ),
            )
        }
    }

    // --- helpers ---

    private fun insertTestVideo(): Long {
        val id = videoIdSeq++
        dsl.execute(
            "INSERT INTO videos (id, user_id, title, status) VALUES (?, 100, 'test', 'DRAFT')", id,
        )
        return id
    }

    private fun newActiveSource(userId: Long) = ContentSource(
        id = 0L, userId = userId, sourceType = ContentSourceType.GOOGLE_DRIVE,
        externalAccountId = "google-sub-$userId", accountEmail = "u@gmail.com",
        accountDisplayName = null, accessTokenEncrypted = "enc:at",
        refreshTokenEncrypted = "enc:rt", tokenExpiresAt = Instant.now().plusSeconds(3600),
        grantedScopes = "drive.readonly", status = ContentSourceStatus.ACTIVE,
        lastError = null, connectedAt = Instant.now(), lastUsedAt = null, updatedAt = Instant.now(),
    )

    private fun newJob(
        userId: Long, videoId: Long, driveFileId: String,
        size: Long = 1000L, transferred: Long = 0L,
        status: DriveImportStatus = DriveImportStatus.PENDING,
    ) = DriveImportJob(
        id = 0L, videoId = videoId, userId = userId, contentSourceId = sourceId,
        driveFileId = driveFileId, driveFileName = "$driveFileId.mp4",
        fileSizeBytes = size, bytesTransferred = transferred,
        status = status, s3Key = null, errorMessage = null, retryCount = 0,
        startedAt = null, completedAt = null,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )
}
