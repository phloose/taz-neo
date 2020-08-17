package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import com.squareup.moshi.JsonClass
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

data class Issue(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    val imprint: Article?,
    override val isWeekend: Boolean,
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList(),
    override val moTime: String,

    // internal
    override val dateDownload: Date?,
    override val downloadedStatus: DownloadStatus?
) : IssueOperations, CacheableDownload {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(feedName, issueDto.date, issueDto.status, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.imprint?.let { Article(feedName, issueDto.date, it, ArticleType.IMPRINT) },
        issueDto.isWeekend,
        issueDto.sectionList?.map { Section(feedName, issueDto.date, it) } ?: emptyList(),
        issueDto.pageList?.map { Page(feedName, issueDto.date, it) } ?: emptyList(),
        issueDto.moTime,
        null,
        DownloadStatus.pending
    )

    override fun getAllFileNames(): List<String> {
        val files = mutableListOf(moment.getAllFileNames())
        imprint?.let {
            files.add(imprint.getAllFileNames())
        }
        sectionList.forEach { section ->
            files.add(section.getAllFileNames())
            getArticleList().forEach { article ->
                files.add(article.getAllFileNames())
            }
        }
        return files.flatten().distinct()
    }

    override fun getDownloadTag(): String? {
        return tag
    }

    override fun getIssueOperations(applicationContext: Context?): IssueOperations? {
        return this
    }

    override fun getLiveData(applicationContext: Context?): LiveData<Issue?> {
        return IssueRepository.getInstance(applicationContext).getIssueLiveData(
            this.feedName, this.date, this.status
        )
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return IssueRepository.getInstance(applicationContext).isDownloadedLiveData(this)
    }

    fun getArticleList(): List<Article> {
        val articleList = mutableListOf<Article>()
        sectionList.forEach {
            articleList.addAll(it.articleList)
        }
        return articleList
    }

    override suspend fun deleteFiles(updateDatabase: Boolean) {
        if (updateDatabase) {
            IssueRepository.getInstance().resetDownloadDate(this)
            this.setDownloadStatusIncludingChildren(DownloadStatus.pending)
        }
        val allFiles = getAllFiles()
        val bookmarkedArticleFiles = sectionList.fold(mutableListOf<String>(), { acc, section ->
            acc.addAll(
                section.articleList.filter { it.bookmarked }.map { it.getAllFileNames() }.flatten()
                    .distinct()
            )
            acc
        })
        allFiles.filter { it.name !in bookmarkedArticleFiles }.forEach {
            it.deleteFile(updateDatabase)
        }
    }

    suspend fun deleteAndUpdateMetaData(applicationContext: Context? = null): Issue? =
        withContext(Dispatchers.IO) {
            val deferredIssue =
                ApiService.getInstance(applicationContext).getIssueByFeedAndDateAsync(
                    feedName, date
                )
            DownloadService.getInstance(applicationContext).cancelDownloads(tag)
            return@withContext deferredIssue.await()?.let {
                val newMetaData = moTime != it.moTime
                deleteFiles(!newMetaData)
                if (newMetaData) {
                    IssueRepository.getInstance(applicationContext).delete(this@Issue)
                    IssueRepository.getInstance(applicationContext).save(it)
                }
                it.moment.download().join()
                it
            }
        }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        IssueRepository.getInstance().update(
            IssueStub(this).copy(downloadedStatus = downloadStatus)
        )
    }

    fun setDownloadStatusIncludingChildren(downloadStatus: DownloadStatus) {
        IssueRepository.getInstance().update(
            IssueStub(this).copy(downloadedStatus = downloadStatus)
        )
        sectionList.forEach { section ->
            section.setDownloadStatus(downloadStatus)
            section.articleList.forEach { article ->
                article.setDownloadStatus(downloadStatus)
            }
        }
        imprint?.setDownloadStatus(downloadStatus)
        pageList.forEach { it.setDownloadStatus(downloadStatus) }
        moment.setDownloadStatus(downloadStatus)
    }

    override fun getDownloadedStatus(applicationContext: Context?): DownloadStatus? {
        return IssueRepository.getInstance(applicationContext)
            .getStub(this)?.downloadedStatus
    }

}

@JsonClass(generateAdapter = false)
enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}

