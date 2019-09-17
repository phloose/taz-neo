package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.util.SingletonHolder

class IssueRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)

    @Transaction
    fun save(issue: Issue) {
        appDatabase.issueDao().insertOrReplace(
            IssueBase(issue)
        )
        // save pages
        pageRepository.save(issue.pageList)

        // save page relation
        appDatabase.issuePageJoinDao().insertOrReplace(
            issue.pageList.mapIndexed { index, page ->
                IssuePageJoin(issue.feedName, issue.date, page.pagePdf.name, index)
            }
        )

        // save imprint
        issue.imprint?.let { imprint ->
            articleRepository.save(imprint)
            appDatabase.issueImprintJoinDao().insertOrReplace(
                IssueImprintJoin(issue.feedName, issue.date, imprint.articleHtml.name)
            )
        }

        // save sections
        issue.sectionList.let { sectionList ->
            sectionList.forEach { sectionRepository.save(it) }
            appDatabase.issueSectionJoinDao().insertOrReplace(sectionList.mapIndexed { index, it ->
                IssueSectionJoin(issue.feedName, issue.date, it.sectionHtml.name, index)
            })
        }
    }

    fun getWithoutFiles(): ResourceInfoWithoutFiles? {
        return appDatabase.resourceInfoDao().get()
    }

    fun getLatestIssueBase(): IssueBase? {
        return appDatabase.issueDao().getLatest()
    }

    fun getLatestIssue(): Issue? {
        return getLatestIssueBase()?.let { issueBaseToIssue(it) }
    }

    @Throws(NotFoundException::class)
    fun getLatestIssueBaseOrThrow(): IssueBase {
        return appDatabase.issueDao().getLatest() ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getLatestIssueOrThrow(): Issue {
        return getLatestIssueBase()?.let { issueBaseToIssue(it) } ?: throw NotFoundException()
    }

    fun getIssueBaseByFeedAndDate(feedName: String, date: String): IssueBase? {
        return appDatabase.issueDao().getByFeedAndDate(feedName, date)
    }

    fun getIssueByFeedAndDate(feedName: String, date: String): Issue? {
        return getIssueBaseByFeedAndDate(feedName, date)?.let{
            issueBaseToIssue(it)
        }
    }

    private fun issueBaseToIssue(issueBase: IssueBase): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueBase)
        val sections = sectionNames.map { sectionRepository.getOrThrow(it) }

        val imprint = appDatabase.issueImprintJoinDao().getImprintNameForIssue(
                issueBase.feedName, issueBase.date
            )?.let { articleRepository.get(it) }

        val pageList =
            appDatabase.issuePageJoinDao().getPageNamesForIssue(issueBase.feedName, issueBase.date)
                .map {
                    pageRepository.getOrThrow(it)
                }

        return Issue(
            issueBase.feedName,
            issueBase.date,
            issueBase.key,
            issueBase.baseUrl,
            issueBase.status,
            issueBase.minResourceVersion,
            issueBase.zipName,
            issueBase.zipPdfName,
            issueBase.navButton,
            imprint,
            issueBase.fileList,
            issueBase.fileListPdf,
            sections,
            pageList
        )

    }


}