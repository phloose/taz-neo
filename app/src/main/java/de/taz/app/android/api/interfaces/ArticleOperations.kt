package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface ArticleOperations: CacheableDownload, WebViewDisplayable  {

    override val key: String
    val articleType: ArticleType

    fun nextArticleStub(): ArticleStub? {
        return ArticleRepository.getInstance().nextArticleStub(this.key)
    }

    fun previousArticleStub(): ArticleStub? {
        return ArticleRepository.getInstance().previousArticleStub(this.key)
    }

    fun getSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getSectionStubForArticle(this.key)
    }

    fun getIndexInSection(): Int? {
        return ArticleRepository.getInstance().getIndexInSection(this.key)
    }

    fun isBookmarkedLiveData(): LiveData<Boolean> {
        return ArticleRepository.getInstance().isBookmarkedLiveData(this.key)
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(this.key)
    }

    override fun getFilePath(): String? {
        return FileHelper.getInstance().getAbsoluteFilePath(this.key)
    }

    override fun previous(): ArticleStub? {
        return previousArticleStub()
    }

    override fun next(): ArticleStub? {
        return nextArticleStub()
    }


    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    fun getIssueStub(): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance().getIssueStubByImprintFileName(this.key)
        } else {
            getSectionStub()?.issueStub
        }
    }

    override fun getIssueOperations() = getIssueStub()

    suspend fun getNavButton(): Image? = withContext(Dispatchers.IO){
        return@withContext this@ArticleOperations.getSectionStub()?.getNavButton()
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return ArticleRepository.getInstance(applicationContext).isDownloadedLiveData(this)
    }

}