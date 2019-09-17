package de.taz.app.android.api.models

import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.persistence.repository.DownloadRepository

data class Section(
    val sectionHtml: FileEntry,
    val title: String,
    val type: SectionType,
    val articleList: List<Article> = emptyList(),
    val imageList: List<FileEntry> = emptyList()
) {
    constructor(sectionDto: SectionDto) : this(
        sectionDto.sectionHtml,
        sectionDto.title,
        sectionDto.type,
        sectionDto.articleList?.map { Article(it) } ?: listOf(),
        sectionDto.imageList ?: listOf()
    )

    fun isDownloaded(): Boolean {
        DownloadRepository.getInstance().let {
            return it.isDownloaded(sectionHtml.name) &&
                    it.isDownloaded(imageList.map { image -> image.name }) &&
                    articleList.firstOrNull { article -> !article.isDownloaded() } == null
        }
    }

}

