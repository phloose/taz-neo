package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "Article")
data class ArticleStub(
    @PrimaryKey val articleFileName: String,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String>,
    val bookmarked: Boolean = false,
    override val articleType: ArticleType,
    val position: Int,
    val percentage: Int,
    override val downloadedStatus: DownloadStatus?
) : ArticleOperations {

    constructor(article: Article) : this(
        article.articleHtml.name,
        article.issueFeedName,
        article.issueDate,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarked,
        article.articleType,
        article.position,
        article.percentage,
        article.downloadedStatus
    )

    @Ignore
    override val key: String = articleFileName

    override fun getAllFileNames(): List<String> {
        val articleRepository = ArticleRepository.getInstance()
        val imageList = articleRepository.getImagesForArticle(key)
        val authorList = articleRepository.getAuthorImageFileNamesForArticle(key)

        val list = mutableListOf(key)
        list.addAll(authorList)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }.map { it.name })
        return list.distinct()
    }

    suspend fun getFirstImage(): Image? = withContext(Dispatchers.IO) {
        ArticleRepository.getInstance().getImagesForArticle(this@ArticleStub.key)
            .firstOrNull()
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        ArticleRepository.getInstance().update(this.copy(downloadedStatus = downloadStatus))
    }

}
