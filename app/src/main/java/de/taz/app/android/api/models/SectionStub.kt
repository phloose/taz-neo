package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.SectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "Section")
data class SectionStub(
    @PrimaryKey val sectionFileName: String,
    val issueDate: String,
    override val title: String,
    val type: SectionType,
    override val extendedTitle: String? = null
) : SectionOperations {

    @Ignore
    override val key: String = sectionFileName

    constructor(section: Section) : this(
        section.sectionHtml.name,
        section.issueDate,
        section.title,
        section.type,
        section.extendedTitle
    )

    override suspend fun getAllFiles(): List<FileEntryOperations> = withContext(Dispatchers.IO) {
        val imageList = SectionRepository.getInstance().imagesForSectionStub(key)
        val list = mutableListOf<FileEntryOperations>(FileEntryRepository.getInstance().getOrThrow(key))
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal })
        return@withContext list.distinct()
    }

    override fun getAllFileNames(): List<String> {
        val list = SectionRepository.getInstance().imagesForSectionStub(
            key
        ).filter { it.resolution == ImageResolution.normal }.map { it.name }.toMutableList()
        list.add(key)
        return list.distinct()
    }

}

