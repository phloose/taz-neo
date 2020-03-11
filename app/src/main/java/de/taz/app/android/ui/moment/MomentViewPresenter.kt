package de.taz.app.android.ui.moment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MomentViewPresenter(
    val issueRepository: IssueRepository = IssueRepository.getInstance()
) :
    MomentViewContract.Presenter,
    BasePresenter<MomentViewContract.View, MomentViewDataController>(
        MomentViewDataController::class.java)
{
    private val log by Log

    private suspend fun downloadMomentAndGenerateImage(issueStub: IssueStub): Bitmap?
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
                                    continuation.resume(generateBitmapForMoment(moment))
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
                    continuation.resume(generateBitmapForMoment(moment))
                }
            } ?: continuation.resume(null)
            val issueLiveData =
                IssueRepository.getInstance().getIssue(issueStub).isDownloadedLiveData()
            withContext(Dispatchers.Main) {
                issueLiveData.observe(
                    lifecycleOwner,
                    Observer { isDownloaded ->
                        if (isDownloaded) {
                            getView()?.hideDownloadIcon()
                        }
                        else {
                            getView()?.showDownloadIcon()
                        }
                    }
                )
            }
        }
    }

    private fun generateBitmapForMoment(moment: Moment): Bitmap? {
        moment.getMomentImage().let {
            val file = FileHelper.getInstance().getFile(it)
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.absolutePath)
            } else {
                log.error("imgFile of $moment does not exist")
            }
        }
        return null
    }

    override fun clearIssue() {
        getView()?.clearIssue()
    }

    override suspend fun setIssue(issueStub: IssueStub, feed: Feed?, dateFormat: DateFormat) {
        clearIssue()
        getView()?.setDimension(feed)
        val bitmap = downloadMomentAndGenerateImage(issueStub)
        bitmap?.let {
            getView()?.displayIssue(bitmap, issueStub.date, dateFormat)
        }
    }
}