package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.SingletonHolder

class FileEntryRepository private constructor(
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FileEntryRepository, Context>(::FileEntryRepository)

    fun save(fileEntry: FileEntry) {
        appDatabase.runInTransaction {
            val fromDB = appDatabase.fileEntryDao().getByName(fileEntry.name)
            fromDB?.let {
                if (fromDB.moTime < fileEntry.moTime)
                    appDatabase.fileEntryDao().insertOrReplace(fileEntry)
            }
        }
    }

    fun save(fileEntries: List<FileEntry>) {
        appDatabase.runInTransaction {
            fileEntries.forEach { save(it) }
        }
    }

    fun get(fileEntryName: String): FileEntry? {
        return appDatabase.fileEntryDao().getByName(fileEntryName)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        return fileEntryNames.map { getOrThrow(it) }
    }

    fun delete(fileEntry: FileEntry) {
        appDatabase.fileEntryDao().delete(fileEntry)
    }

}