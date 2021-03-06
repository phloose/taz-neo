package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.SectionRepository

data class Section(
    val sectionHtml: FileEntry,
    val issueDate: String,
    override val title: String,
    val type: SectionType,
    val navButton: Image,
    val articleList: List<Article>,
    val imageList: List<Image>,
    override val extendedTitle: String?,
    override val downloadedStatus: DownloadStatus?
) : SectionOperations {

    constructor(issueFeedName: String, issueDate: String, sectionDto: SectionDto) : this(
        sectionHtml = FileEntry(sectionDto.sectionHtml, "$issueFeedName/$issueDate"),
        issueDate = issueDate,
        title = sectionDto.title,
        type = sectionDto.type,
        navButton = Image(sectionDto.navButton, "$issueFeedName/$issueDate"),
        articleList = sectionDto.articleList?.map { Article(issueFeedName, issueDate, it) }
            ?: listOf(),
        imageList = sectionDto.imageList?.map { Image(it, "$issueFeedName/$issueDate") }
            ?: listOf(),
        extendedTitle = sectionDto.extendedTitle,
        downloadedStatus = DownloadStatus.pending
    )

    override val key: String
        get() = sectionHtml.name

    override suspend fun getAllFiles(): List<FileEntryOperations> {
        val list = mutableListOf<FileEntryOperations>(sectionHtml)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal })
        return list.distinct()
    }

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf(sectionHtml.name)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }.map { it.name })
        return list.distinct()
    }

    override fun getAllLocalFileNames(): List<String> {
        val list = mutableListOf(sectionHtml.name)
        list.addAll(imageList.filter { it.downloadedStatus == DownloadStatus.done }.map { it.name })
        return list.distinct()
    }

    override fun getLiveData(applicationContext: Context?): LiveData<Section?> {
        return SectionRepository.getInstance(applicationContext).getLiveData(sectionHtml.name)
    }

}

