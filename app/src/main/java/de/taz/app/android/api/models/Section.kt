package de.taz.app.android.api.models

import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.SectionOperations

data class Section(
    val sectionHtml: FileEntry,
    val issueDate: String,
    override val title: String,
    val type: SectionType,
    val navButton: Image,
    val articleList: List<Article> = emptyList(),
    val imageList: List<FileEntry> = emptyList(),
    override val extendedTitle: String? = null
) : SectionOperations, CacheableDownload {
    constructor(issueFeedName: String, issueDate: String, sectionDto: SectionDto) : this(
        sectionHtml = FileEntry(sectionDto.sectionHtml, "$issueFeedName/$issueDate"),
        issueDate = issueDate,
        title = sectionDto.title,
        type = sectionDto.type,
        navButton = Image(sectionDto.navButton, "$issueFeedName/$issueDate"),
        articleList = sectionDto.articleList?.map { Article(issueFeedName, issueDate, it) } ?: listOf(),
        imageList = sectionDto.imageList?.map { FileEntry(it, "$issueFeedName/$issueDate") } ?: listOf(),
        extendedTitle = sectionDto.extendedTitle
    )

    override val key: String
        get() = sectionHtml.name

    override suspend fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(sectionHtml)
        list.addAll(imageList.filter { it.name.contains(".norm.") })
        return list.distinct()
    }

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf(sectionHtml.name)
        list.addAll(imageList.map { it.name }.filter { it.contains(".norm.") })
        return list.distinct()
    }
}

