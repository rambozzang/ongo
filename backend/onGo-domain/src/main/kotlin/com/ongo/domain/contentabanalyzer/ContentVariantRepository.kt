package com.ongo.domain.contentabanalyzer

interface ContentVariantRepository {
    fun findByTestId(testId: Long): List<ContentVariant>
    fun save(variant: ContentVariant): ContentVariant
}
