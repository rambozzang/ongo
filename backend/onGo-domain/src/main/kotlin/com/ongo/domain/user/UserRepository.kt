package com.ongo.domain.user

import com.ongo.common.enums.AuthProvider

interface UserRepository {
    fun findById(id: Long): User?

    /**
     * 사용자 행을 **잠그고** 읽는다. 트랜잭션이 끝날 때까지 같은 행을 읽으려는 다른
     * 트랜잭션이 대기한다.
     *
     * 한 사용자를 기준으로 "세어 보고 넣는" 판정을 직렬화할 때 쓴다. 그런 판정은
     * READ COMMITTED 에서 두 요청이 **커밋 전 같은 수**를 읽어 둘 다 통과한다. 값을 읽는
     * 것만으로는 막을 수 없고, 기준이 되는 행을 잠가야 한다.
     */
    fun findByIdForUpdate(id: Long): User?
    fun findByEmail(email: String): User?
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
    fun findByPaddleCustomerId(paddleCustomerId: String): User?
    fun findAll(offset: Int, limit: Int, searchQuery: String?): List<User>
    fun countAll(searchQuery: String?): Long
    fun save(user: User): User
    fun update(user: User): User
    fun delete(id: Long)
}
