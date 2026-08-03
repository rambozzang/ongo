package com.ongo.domain.ugc.shorts

interface ClipValidationRepository {
    fun saveAll(validations: List<ClipValidation>): List<ClipValidation>
}
