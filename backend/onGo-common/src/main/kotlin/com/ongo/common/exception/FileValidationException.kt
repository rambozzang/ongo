package com.ongo.common.exception

class FileValidationException(message: String) :
    BusinessException("FILE_VALIDATION_FAILED", message)
