package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoStub
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin
import de.taz.app.android.util.SingletonHolder

class ResourceInfoRepository private constructor(applicationContext: Context): RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ResourceInfoRepository, Context>(::ResourceInfoRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun save(resourceInfo: ResourceInfo) {
        appDatabase.runInTransaction {
            appDatabase.resourceInfoDao().insertOrReplace(
                ResourceInfoStub(
                    resourceInfo.resourceVersion,
                    resourceInfo.resourceBaseUrl,
                    resourceInfo.resourceZip
                )
            )
            // save file resourceList
            fileEntryRepository.save(
                resourceInfo.resourceList.map { FileEntry(it) }
            )
            // save relation to files
            appDatabase.resourceInfoFileEntryJoinDao().insertOrReplace(
                resourceInfo.resourceList.mapIndexed { index, fileEntry ->
                    ResourceInfoFileEntryJoin(resourceInfo.resourceVersion, fileEntry.name, index)
                }
            )
        }
    }

    @Throws(NotFoundException::class)
    fun getWithoutFilesOrThrow(): ResourceInfoStub {
        getWithoutFiles()?.let {
            return it
        }
        throw NotFoundException()
    }

    fun getWithoutFiles(): ResourceInfoStub? {
        return appDatabase.resourceInfoDao().get()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(): ResourceInfo {
        val resourceInfoStub = appDatabase.resourceInfoDao().get()
        val resourceList = appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(
            resourceInfoStub.resourceVersion
        )
        return ResourceInfo(
            resourceInfoStub.resourceVersion,
            resourceInfoStub.resourceBaseUrl,
            resourceInfoStub.resourceZip,
            resourceList.map { FileEntry(it.name, it.storageType, it.moTime, it.sha256, it.size) }
        )
    }

    fun get(): ResourceInfo? {
        return try {
            getOrThrow()
        } catch (e: Exception) {
            null
        }
    }

    fun delete(resourceInfo: ResourceInfo) {
        appDatabase.resourceInfoDao().delete(ResourceInfoStub(resourceInfo))
    }
}