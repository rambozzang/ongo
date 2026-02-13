package com.ongo.common.exception

class UnauthorizedException(message: String = "인증이 필요합니다") :
    BusinessException("UNAUTHORIZED", message)
