package com.ongo.common.exception

class ForbiddenException(message: String = "접근 권한이 없습니다") :
    BusinessException("FORBIDDEN", message)
