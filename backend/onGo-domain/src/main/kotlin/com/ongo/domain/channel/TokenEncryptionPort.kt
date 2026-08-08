package com.ongo.domain.channel

/**
 * Domain port interface for token encryption/decryption.
 * Implemented by infrastructure layer (TokenEncryptionService).
 */
interface TokenEncryptionPort {
    fun encrypt(plainText: PlainToken): EncryptedToken
    fun decrypt(cipherText: EncryptedToken): PlainToken
}
