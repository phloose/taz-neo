package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import com.squareup.moshi.JsonClass
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        val files = mutableListOf<List<String>>()
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

    override fun getAllLocalFileNames(): List<String> {
        val files = mutableListOf<List<String>>()
        imprint?.let {
            files.add(imprint.getAllLocalFileNames())
        }
        sectionList.forEach { section ->
            files.add(section.getAllLocalFileNames())
            getArticleList().forEach { article ->
                files.add(article.getAllLocalFileNames())
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

    private fun getArticleList(): List<Article> {
        val articleList = mutableListOf<Article>()
        sectionList.forEach {
            articleList.addAll(it.articleList)
        }
        return articleList
    }

    override suspend fun deleteFiles() {
        val filesToDelete: MutableList<FileEntryOperations> = getAllLocalFiles().toMutableList()
        val filesToRetain = sectionList.fold(mutableListOf<String>()) { acc, section ->
            // bookmarked articles should remain
            acc.addAll(
                section.articleList
                    .filter { it.bookmarked }
                    .map { it.getAllFileNames() }
                    .flatten()
                    .distinct()
            )
            // author images are potentiall used globally so we retain them for now as they don't eat up much space
            acc.addAll(
                section.articleList
                    .map { it.authorList }
                    .flatten()
                    .mapNotNull { it.imageAuthor }
                    .map { it.name }
            )
            acc
        }
        filesToDelete.removeAll { it.name in filesToRetain }

        // do not delete files of other issues of the day
        IssueRepository.getInstance().getDownloadedOrDownloadingIssuesForDayAndFeed(feedName, date)
            .forEach {
                if (it.status != status) {
                    if (it.downloadedStatus in listOf(
                            DownloadStatus.started,
                            DownloadStatus.done
                        )
                    ) {
                        filesToDelete.removeAll(it.getAllFiles())
                    }
                }
            }

        // do not delete bookmarked files
        ArticleRepository.getInstance().getBookmarkedArticleStubListForIssuesAtDate(feedName, date).forEach {
            filesToDelete.removeAll(it.getAllFiles())
        }

        filesToDelete.forEach { it.deleteFile() }

        this.setDownloadStatusIncludingChildren(DownloadStatus.pending)
        IssueRepository.getInstance().resetDownloadDate(this)
    }

    /**
     * function to delete all files
     * the server will be asked if the metadata has changed
     * if the metadata has changed it will be saved to the Database
     *
     * @return the new [Issue] if the metadata has changed else null
     */
    suspend fun deleteAndUpdateMetaData(applicationContext: Context? = null): Issue? =
        withContext(Dispatchers.IO) {
            DownloadService.getInstance(applicationContext).cancelDownloadsForTag(tag)
            deleteFiles()
            moment.deleteFiles()
            try {
                val issue =
                    ApiService.getInstance(applicationContext).getIssueByFeedAndDate(
                        feedName, date
                    )
                issue?.let {
                    val newMetaData = moTime != it.moTime || status != it.status
                    it.moment.download().join()
                    if (newMetaData) {
                        IssueRepository.getInstance(applicationContext).delete(this@Issue)
                        IssueRepository.getInstance(applicationContext).save(it)
                        it
                    } else {
                        null
                    }
                }
            } catch (nie: ApiService.ApiServiceException) {
                null
            }
        }

    suspend fun delete(applicationContext: Context? = null) = withContext(Dispatchers.IO) {
        DownloadService.getInstance(applicationContext).cancelDownloadsForTag(tag)
        deleteFiles()
        IssueRepository.getInstance(applicationContext).delete(this@Issue)
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        IssueRepository.getInstance().apply {
            getStub(this@Issue)?.let {
                update(it.copy(downloadedStatus = downloadStatus))
            }
        }
    }

    fun setDownloadStatusIncludingChildren(downloadStatus: DownloadStatus) {
        IssueRepository.getInstance().setDownloadStatusIncludingChildren(this, downloadStatus)
    }

    override fun getDownloadedStatus(applicationContext: Context?): DownloadStatus? {
        return IssueRepository.getInstance(applicationContext)
            .getStub(this)?.downloadedStatus
    }

    override fun download(applicationContext: Context?): Job {
        moment.download(applicationContext)
        return super.download(applicationContext)
    }

}

@JsonClass(generateAdapter = false)
enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}
