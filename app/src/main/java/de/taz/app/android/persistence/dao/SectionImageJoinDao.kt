package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.SectionImageJoin


@Dao
abstract class SectionImageJoinDao : BaseDao<SectionImageJoin>() {

    @Query(
        """SELECT FileEntry.* FROM FileEntry INNER JOIN SectionImageJoin
        ON FileEntry.name = SectionImageJoin.imageFileName
        WHERE SectionImageJoin.sectionFileName == :sectionFileName
        ORDER BY SectionImageJoin.`index` ASC
    """
    )
    abstract fun getImagesForSection(sectionFileName: String): List<FileEntry>

    @Query(
        """SELECT FileEntry.name FROM FileEntry INNER JOIN SectionImageJoin
        ON FileEntry.name = SectionImageJoin.imageFileName
        WHERE SectionImageJoin.sectionFileName == :sectionFileName
        ORDER BY SectionImageJoin.`index` ASC
    """
    )
    abstract fun getImageNamesForSection(sectionFileName: String): List<String>

    fun getImagesForSection(section: Section): List<FileEntry> =
        getImagesForSection(section.sectionHtml.name)

    fun getImagesForSection(sectionStub: SectionStub): List<FileEntry> =
        getImagesForSection(sectionStub.sectionFileName)
}
