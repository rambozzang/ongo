package com.ongo.common.config

data class PageRequest(
    val page: Int = 0,
    val size: Int = 20,
    val sort: String? = null,
    val direction: String = "DESC",
) {
    val offset: Long get() = safePage.toLong() * safeSize

    val safePage: Int get() = page.coerceAtLeast(0)
    val safeSize: Int get() = size.coerceIn(1, 100)
}
