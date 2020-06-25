package de.taz.app.android.api.models

import de.taz.app.android.api.dto.MomentDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository

data class Moment(
    val imageList: List<Image> = emptyList(),
    val creditList: List<Image> = emptyList()
) : CacheableDownload {
    constructor(issueFeedName: String, issueDate: String, momentDto: MomentDto) : this(
        momentDto.imageList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        momentDto.creditList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList()
    )

    override val downloadedStatus: DownloadStatus?
        get() {
            val imagesToDownload = getImagesToDownload()
            if (imagesToDownload.firstOrNull { it.downloadedStatus == DownloadStatus.aborted } != null) {
                return DownloadStatus.aborted
            }
            if (imagesToDownload.firstOrNull { it.downloadedStatus == DownloadStatus.started } != null) {
                return DownloadStatus.started
            }
            if (imagesToDownload.firstOrNull { it.downloadedStatus == DownloadStatus.pending } != null) {
                return DownloadStatus.pending
            }
            if (imagesToDownload.firstOrNull { it.downloadedStatus == DownloadStatus.takeOld } != null) {
                return DownloadStatus.takeOld
            }
            return DownloadStatus.done
        }

    private fun getImagesToDownload(): List<Image> {
        return imageList.filter { it.resolution == ImageResolution.high }.distinct()
    }

    override suspend fun getAllFiles(): List<Image> {
        return getImagesToDownload()
    }

    override fun getAllFileNames(): List<String> {
        return getImagesToDownload().map { it.name }
    }

    fun getMomentFileToShare(): FileEntryOperations {
        return if (creditList.isNotEmpty()) {
            creditList.first { it.resolution == ImageResolution.high }
        } else {
            imageList.first { it.resolution == ImageResolution.high }
        }
    }

    private fun getIssueStub(): IssueStub? {
        return IssueRepository.getInstance().getIssueStubForMoment(this)
    }

    override fun getIssueOperations(): IssueOperations? {
        return getIssueStub()
    }

    fun getMomentImage(): Image? {
        return imageList.firstOrNull { it.resolution == ImageResolution.high }
            ?: imageList.firstOrNull { it.resolution == ImageResolution.normal }
            ?: imageList.firstOrNull { it.resolution == ImageResolution.small }
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        getImagesToDownload().forEach {
            FileEntryRepository.getInstance().update(
                FileEntry(it).copy(downloadedStatus = downloadStatus)
            )
        }
    }

}