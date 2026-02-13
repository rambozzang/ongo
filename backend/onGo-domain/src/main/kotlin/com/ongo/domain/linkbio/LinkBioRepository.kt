package com.ongo.domain.linkbio

interface LinkBioRepository {
    fun findPageById(id: Long): LinkBioPage?
    fun findPageByUserId(userId: Long): LinkBioPage?
    fun findPageBySlug(slug: String): LinkBioPage?
    fun savePage(page: LinkBioPage): LinkBioPage
    fun updatePage(page: LinkBioPage): LinkBioPage
    fun incrementViewCount(pageId: Long)
    fun findLinksByPageId(pageId: Long): List<LinkBioLink>
    fun saveLink(link: LinkBioLink): LinkBioLink
    fun updateLink(link: LinkBioLink): LinkBioLink
    fun deleteLink(id: Long)
    fun deleteAllLinksByPageId(pageId: Long)
    fun incrementClickCount(linkId: Long)
}
