package com.ongo.common

import com.ongo.common.config.PageRequest
import com.ongo.common.config.PageResponse
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.MediaType
import com.ongo.common.exception.FileValidationException
import com.ongo.common.util.AESEncryptionUtil
import com.ongo.common.util.DateTimeUtil
import com.ongo.common.util.FileValidationUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommonContractTest {
    private val encryptionKey = "01234567890123456789012345678901"

    @Test
    fun `AES GCM round trip preserves Unicode and uses a fresh IV`() {
        val first = AESEncryptionUtil.encrypt("민감한 토큰 🔐", encryptionKey)
        val second = AESEncryptionUtil.encrypt("민감한 토큰 🔐", encryptionKey)

        assertNotEquals(first, second)
        assertEquals("민감한 토큰 🔐", AESEncryptionUtil.decrypt(first, encryptionKey))
        assertEquals("민감한 토큰 🔐", AESEncryptionUtil.decrypt(second, encryptionKey))
    }

    @Test
    fun `AES GCM rejects invalid key and tampered ciphertext`() {
        assertThrows<IllegalArgumentException> {
            AESEncryptionUtil.encrypt("token", "too-short")
        }
        val encrypted = AESEncryptionUtil.encrypt("token", encryptionKey)
        val tampered = encrypted.dropLast(2) + "AA"
        assertThrows<Exception> {
            AESEncryptionUtil.decrypt(tampered, encryptionKey)
        }
    }

    @Test
    fun `file validation accepts matching video and image contracts`() {
        FileValidationUtil.validate("clip.mp4", "video/mp4", 1024)
        FileValidationUtil.validateImage("poster.PNG", "image/png", 1024)
        assertEquals(MediaType.VIDEO, FileValidationUtil.detectMediaType("video/quicktime"))
        assertEquals(MediaType.IMAGE, FileValidationUtil.detectMediaType("image/webp"))
    }

    @Test
    fun `file validation rejects extension mime size and media mismatches`() {
        assertThrows<FileValidationException> {
            FileValidationUtil.validate("clip.mp4", "video/quicktime", 1024)
        }
        assertThrows<FileValidationException> {
            FileValidationUtil.validate("clip.exe", "application/octet-stream", 1024)
        }
        assertThrows<FileValidationException> {
            FileValidationUtil.validateFileSize(0)
        }
        assertThrows<FileValidationException> {
            FileValidationUtil.validateImage("poster.png", "image/png", 51L * 1024 * 1024)
        }
        assertThrows<FileValidationException> {
            FileValidationUtil.detectMediaType("application/pdf")
        }
    }

    @Test
    fun `media validation routes video and image contracts`() {
        FileValidationUtil.validateByMediaType("clip.webm", "video/webm", 1024, MediaType.VIDEO)
        FileValidationUtil.validateByMediaType("cover.webp", "image/webp", 1024, MediaType.IMAGE)

        assertThrows<FileValidationException> {
            FileValidationUtil.validateByMediaType("cover.webp", "image/webp", 1024, MediaType.VIDEO)
        }
        assertThrows<FileValidationException> {
            FileValidationUtil.validateByMediaType("clip.mp4", "video/mp4", 1024, MediaType.IMAGE)
        }
    }

    @Test
    fun `content validation rejects spoofed media and accepts real container signatures`() {
        FileValidationUtil.validateVideoContent(
            ByteArrayInputStream(byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70)),
            "video/mp4",
        )
        FileValidationUtil.validateAssetContent(
            ByteArrayInputStream(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
            "image/png",
        )
        assertThrows<FileValidationException> {
            FileValidationUtil.validateVideoContent(ByteArrayInputStream("not a video".encodeToByteArray()), "video/mp4")
        }
        assertThrows<FileValidationException> {
            FileValidationUtil.validateAssetContent(ByteArrayInputStream("not a pdf".encodeToByteArray()), "application/pdf")
        }
    }

    @Test
    fun `credit packages expose the published pricing and validity contract`() {
        assertEquals(4, CreditPackage.entries.size)
        assertEquals(500, CreditPackage.STARTER.credits)
        assertEquals(4_900, CreditPackage.STARTER.price)
        assertEquals(10_000, CreditPackage.BUSINESS.credits)
        assertEquals(180, CreditPackage.BUSINESS.validDays)

        CreditPackage.entries.forEach { creditPackage ->
            assertTrue(creditPackage.credits > 0)
            assertTrue(creditPackage.price > 0)
            assertTrue(creditPackage.validDays > 0)
        }

        assertTrue(CreditPackage.entries.zipWithNext().all { (current, next) ->
            current.credits < next.credits &&
                current.price < next.price &&
                current.validDays < next.validDays
        })
    }

    @Test
    fun `page request clamps unsafe values and page response derives navigation`() {
        val request = PageRequest(page = -3, size = 1000)
        assertEquals(0, request.safePage)
        assertEquals(100, request.safeSize)
        assertEquals(0, request.offset)

        val first = PageResponse.of(listOf("a"), page = 0, size = 1, totalElements = 2)
        assertEquals(2, first.totalPages)
        assertTrue(first.hasNext)
        assertTrue(!first.hasPrevious)
        val last = PageResponse.of(listOf("b"), page = 1, size = 1, totalElements = 2)
        assertTrue(!last.hasNext)
        assertTrue(last.hasPrevious)
    }

    @Test
    fun `date time helpers format in the product timezone`() {
        val value = LocalDateTime.of(2026, 8, 9, 15, 4, 5)
        assertEquals("2026-08-09", DateTimeUtil.formatDate(value))
        assertEquals("2026-08-09 15:04:05", DateTimeUtil.formatDateTime(value))
        assertEquals("2026-08-09", DateTimeUtil.toKST(java.time.Instant.parse("2026-08-09T06:00:00Z")).toLocalDate().toString())
    }
}
