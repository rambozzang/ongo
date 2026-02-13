package com.ongo.common.exception

class CreditNotFoundException(userId: Long) :
    BusinessException("CREDIT_NOT_FOUND", "크레딧 정보를 찾을 수 없습니다. userId: $userId")
