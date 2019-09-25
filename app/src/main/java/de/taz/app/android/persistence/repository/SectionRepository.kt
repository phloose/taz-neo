package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Transaction
import de.taz.app.android.api.interfaces.SectionFunctions
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.util.SingletonHolder

class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    @Transaction
    fun save(section: Section) {
        appDatabase.sectionDao().insertOrReplace(SectionBase(section))
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
        fileEntryRepository.save(section.imageList)
        appDatabase.sectionImageJoinDao()
            .insertOrReplace(section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            })
    }

    fun getBase(sectionFileName: String): SectionBase? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getBaseOrThrow(sectionFileName: String): SectionBase {
        return getBase(sectionFileName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(sectionFileName: String): Section {
        return sectionBaseToSection(getBaseOrThrow(sectionFileName))
    }

    fun get(sectionFileName: String): Section? {
        return try {
            getOrThrow(sectionFileName)
        } catch (nfe: NotFoundException) {
            null
        }
    }

    fun getSectionBaseForArticle(articleFileName: String): SectionBase? {
        return appDatabase.sectionArticleJoinDao().getSectionBaseForArticleFileName(articleFileName)
    }

    @Throws(NotFoundException::class)
    fun getSectionForArticle(articleFileName: String): Section? {
        return getSectionBaseForArticle(articleFileName)?.let { sectionBaseToSection(it) }
    }

    fun getNextSectionBase(sectionFileName: String): SectionBase? {
        return appDatabase.sectionDao().getNext(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getNextSection(sectionFileName: String): Section? =
        getNextSectionBase(sectionFileName)?.let { sectionBaseToSection(it) }

    @Throws(NotFoundException::class)
    fun getNextSection(section: SectionFunctions): Section? =
        getNextSection(section.sectionFileName)

    fun getPreviousSectionBase(sectionFileName: String): SectionBase? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getPreviousSection(sectionFileName: String): Section? =
        getPreviousSectionBase(sectionFileName)?.let { sectionBaseToSection(it) }

    @Throws(NotFoundException::class)
    fun getPreviousSection(section: SectionFunctions): Section? =
        getPreviousSection(section.sectionFileName)

    @Throws(NotFoundException::class)
    fun sectionBaseToSection(sectionBase: SectionBase): Section {
        val sectionFileName = sectionBase.sectionFileName
        val sectionFile = fileEntryRepository.getOrThrow(sectionFileName)

        val articles =
            appDatabase.sectionArticleJoinDao().getArticleFileNamesForSection(sectionFileName)?.let {
                articleRepository.getOrThrow(it)
            } ?: listOf()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        images?.let {
            return Section(
                sectionFile,
                sectionBase.title,
                sectionBase.type,
                articles,
                images
            )
        } ?: throw NotFoundException()
    }

}

