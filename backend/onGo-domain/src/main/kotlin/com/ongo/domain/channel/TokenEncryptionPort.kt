package com.ongo.domain.channel

/**
 * Domain port interface for token encryption/decryption.
 * Implemented by infrastructure layer (TokenEncryptionService).
 */
interface TokenEncryptionPort {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}
