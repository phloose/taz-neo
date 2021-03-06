package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.*
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.persistence.join.SectionNavButtonJoin
import de.taz.app.android.util.SingletonHolder

@Mockable
class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)

    fun save(section: Section) {
        appDatabase.sectionDao().insertOrReplace(SectionStub(section))
        fileEntryRepository.save(section.sectionHtml)
        section.articleList.forEach { articleRepository.save(it) }
        appDatabase.sectionArticleJoinDao().insertOrReplace(
            section.articleList.mapIndexed { index, article ->
                SectionArticleJoin(
                    section.sectionHtml.name,
                    article.articleHtml.name,
                    index
                )
            }
        )
        imageRepository.save(section.imageList)
        appDatabase.sectionImageJoinDao()
            .insertOrReplace(section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            })

        imageRepository.save(section.navButton)

        appDatabase.sectionNavButtonJoinDao().insertOrReplace(
            SectionNavButtonJoin(
                sectionFileName = section.sectionHtml.name,
                navButtonFileName = section.navButton.name
            )
        )

    }

    fun update(sectionStub: SectionStub) {
        appDatabase.sectionDao().update(sectionStub)
    }

    fun getStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getStubOrThrow(sectionFileName: String): SectionStub {
        return getStub(sectionFileName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(sectionFileName: String): Section {
        return sectionStubToSection(getStubOrThrow(sectionFileName))
    }

    fun getStubLiveData(sectionFileName: String): LiveData<SectionStub?> {
        return appDatabase.sectionDao().getLiveData(sectionFileName)
    }

    fun getLiveData(sectionFileName: String): LiveData<Section?> {
        return Transformations.map(getStubLiveData(sectionFileName)) {
            it?.let {
                sectionStubToSection(it)
            }
        }
    }

    fun get(sectionFileName: String): Section? {
        return try {
            getOrThrow(sectionFileName)
        } catch (nfe: NotFoundException) {
            null
        }
    }

    fun getSectionStubForArticle(articleFileName: String): SectionStub? {
        return appDatabase.sectionArticleJoinDao().getSectionStubForArticleFileName(articleFileName)
    }

    fun getNextSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getNext(sectionFileName)
    }

    fun getSectionStubsLiveDataForIssueOperations(issueOperations: IssueOperations) =
        getSectionStubsLiveDataForIssueOperations(
            issueOperations.feedName,
            issueOperations.date,
            issueOperations.status
        )

    fun getSectionStubsLiveDataForIssueOperations(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): LiveData<List<SectionStub>> {
        return appDatabase.sectionDao().getSectionsLiveDataForIssue(
            issueFeedName, issueDate, issueStatus
        )
    }

    fun getSectionStubsForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueFeedName, issueDate, issueStatus
        )
    }

    fun getSectionStubsForIssue(
        issueKey: IssueKey
    ): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueKey.feedName, issueKey.date, issueKey.status
        )
    }

    fun getPreviousSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    fun imagesForSectionStub(sectionFileName: String): List<Image> {
        return appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun sectionStubToSection(sectionStub: SectionStub): Section {
        val sectionFileName = sectionStub.sectionFileName
        val sectionFile = fileEntryRepository.getOrThrow(sectionFileName)

        val articles =
            appDatabase.sectionArticleJoinDao().getArticleFileNamesForSection(sectionFileName)
                ?.let {
                    articleRepository.getOrThrow(it)
                } ?: listOf()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        val navButton =
            appDatabase.sectionNavButtonJoinDao().getNavButtonForSection(sectionFileName)

        images.let {
            return Section(
                sectionHtml = sectionFile,
                issueDate = sectionStub.issueDate,
                title = sectionStub.title,
                type = sectionStub.type,
                navButton = navButton,
                articleList = articles,
                imageList = images,
                extendedTitle = sectionStub.extendedTitle,
                downloadedStatus = sectionStub.downloadedStatus
            )
        }
    }

    fun delete(section: Section) {
        appDatabase.sectionArticleJoinDao().delete(
            section.articleList.mapIndexed { index, article ->
                SectionArticleJoin(
                    section.sectionHtml.name,
                    article.articleHtml.name,
                    index
                )
            }
        )

        section.articleList.forEach { article ->
            articleRepository.deleteArticle(article)
        }

        fileEntryRepository.delete(section.sectionHtml)

        appDatabase.sectionImageJoinDao().delete(
            section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            }
        )

        section.imageList.forEach {
            try {
                imageRepository.delete(it)
            } catch (e: SQLiteConstraintException) {
                log.warn("FileEntry ${it.name} not deleted, maybe still used by a bookmarked article?")
                // do not delete still used by (presumably bookmarked) article
            }
        }

        appDatabase.sectionNavButtonJoinDao().delete(
            SectionNavButtonJoin(
                section.sectionHtml.name,
                section.navButton.name
            )
        )

        try {
            imageRepository.delete(section.navButton)
        } catch (e: SQLiteConstraintException) {
            // do not delete still used
        }

        appDatabase.sectionDao().delete(SectionStub(section))
    }

    fun getNavButton(sectionFileName: String): Image {
        return appDatabase.sectionNavButtonJoinDao().getNavButtonForSection(sectionFileName)
    }

    fun isDownloadedLiveData(sectionOperations: SectionOperations): LiveData<Boolean> {
        return appDatabase.sectionDao().isDownloadedLiveData(sectionOperations.key)
    }

}

