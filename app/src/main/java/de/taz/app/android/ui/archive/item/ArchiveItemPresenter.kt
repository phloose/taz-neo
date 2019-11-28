package de.taz.app.android.ui.archive.item

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ArchiveItemPresenter :
    ArchiveItemContract.Presenter,
    BasePresenter<ArchiveItemContract.View, ArchiveItemDataController>(ArchiveItemDataController::class.java)
{
    private val log by Log

    override fun onViewCreated(savedInstanceState: Bundle?) {

    }


    suspend fun downloadMomentAndGenerateImage(issueStub: IssueStub): Bitmap?
            = suspendCoroutine { continuation ->
        val lifecycleOwner = getView()!!.getLifecycleOwner()
        val lifecycleScope = lifecycleOwner.lifecycleScope

        lifecycleScope.launch(Dispatchers.IO) {
            val moment = MomentRepository.getInstance().get(issueStub)
            moment?.let {
                if (!it.isDownloaded()) {
                    log.debug("requesting download of $moment")

                    DownloadService.download(getView()!!.getContext(), moment)

                    val waitForDownloadObserver = object: Observer<Boolean> {
                        override fun onChanged(isDownloaded: Boolean) {
                            if (isDownloaded) {
                                log.debug("moment is downloaded: $moment")
                                moment.isDownloadedLiveData().removeObserver(this)
                                lifecycleScope.launch(
                                    Dispatchers.IO
                                ) {
                                    log.debug("generating image for $moment")
                                    continuation.resume(generateBitmapForMoment(issueStub, moment))
                                }
                            } else {
                                log.debug("waiting for not yet downloaded: $moment")
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        moment.isDownloadedLiveData().observe(lifecycleOwner, waitForDownloadObserver)
                    }
                } else {
                    continuation.resume(generateBitmapForMoment(issueStub, moment))
                }
            } ?: continuation.resume(null)
        }
    }

    private fun generateBitmapForMoment(issueStub: IssueStub, moment: Moment): Bitmap? {
        // get biggest image -> TODO save image resolution?
        return moment.imageList.lastOrNull()?.let {
            val imgFile = FileHelper.getInstance().getFile("${issueStub.tag}/${it.name}")

            if (imgFile.exists()) {
                BitmapFactory.decodeFile(imgFile.absolutePath)
            } else {
                log.error("imgFile of $moment does not exist")
                null
            }
        }
    }

    override fun clearIssue() {
        getView()?.clearIssue()
    }

    override suspend fun setIssue(issueStub: IssueStub, feed: Feed?) {
        clearIssue()
        getView()?.setDimension(feed?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO)
        val bitmap = downloadMomentAndGenerateImage(issueStub)
        bitmap?.let {
            getView()?.displayIssue(bitmap, issueStub.date)
        }
    }
}