package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.singletons.FileHelper
import kotlinx.serialization.Serializable

const val GLOBAL_FOLDER = "global"

@Entity(tableName = "FileEntry")
@Serializable
data class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    override val folder: String
): FileEntryOperations {

    constructor(fileEntryDto: FileEntryDto, folder: String) : this(
        fileEntryDto.name,
        fileEntryDto.storageType,
        fileEntryDto.moTime,
        fileEntryDto.sha256,
        fileEntryDto.size,
        FileEntryOperations.getStorageFolder(fileEntryDto.storageType, folder)
    )

}
