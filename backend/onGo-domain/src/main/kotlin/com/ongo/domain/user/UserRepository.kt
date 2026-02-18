package com.ongo.domain.user

import com.ongo.common.enums.AuthProvider

interface UserRepository {
    fun findById(id: Long): User?
    fun findByEmail(email: String): User?
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
    fun findAll(offset: Int, limit: Int, searchQuery: String?): List<User>
    fun countAll(searchQuery: String?): Long
    fun save(user: User): User
    fun update(user: User): User
    fun delete(id: Long)
}
