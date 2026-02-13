package com.ongo.common.exception

class DuplicateResourceException(resource: String, field: String) :
    BusinessException("DUPLICATE", "이미 존재하는 $resource 입니다. ($field)")
