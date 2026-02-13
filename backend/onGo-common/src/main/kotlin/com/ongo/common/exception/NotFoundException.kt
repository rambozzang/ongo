package com.ongo.common.exception

class NotFoundException(resource: String, id: Any) :
    BusinessException("NOT_FOUND", "$resource 을(를) 찾을 수 없습니다. id: $id")
