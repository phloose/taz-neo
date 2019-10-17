package de.taz.app.android.api.interfaces

import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.DownloadRepository

interface CacheableDownload {

    fun isDownloadedLiveData(): LiveData<Boolean> {
        return DownloadRepository.getInstance().isDownloadedLiveData(getAllFileNames())
    }

    fun isDownloaded(): Boolean {
        return DownloadRepository.getInstance().isDownloaded(getAllFileNames())
    }

    fun isDownloadedOrDownloading(): Boolean {
        return DownloadRepository.getInstance().isDownloadedOrDownloading(getAllFileNames())
    }

    fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    fun getAllFiles(): List<FileEntry>

    fun getDownloadTag(): String? = null
}