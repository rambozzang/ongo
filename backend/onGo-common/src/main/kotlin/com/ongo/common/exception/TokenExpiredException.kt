package com.ongo.common.exception

class TokenExpiredException(message: String = "토큰이 만료되었습니다") :
    BusinessException("TOKEN_EXPIRED", message)
