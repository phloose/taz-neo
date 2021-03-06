package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.*


class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result = coroutineScope {
        val apiService = ApiService.getInstance(applicationContext)
        apiService.getLastIssues(limit = 1).firstOrNull()?.let { issue ->
            val issueRepository = IssueRepository.getInstance(applicationContext)
            issueRepository.saveIfDoesNotExist(issue)

            log.debug("starting to download - issueDate: ${issue.date} issueFeedName: ${issue.feedName} issueStatus: ${issue.status}")

            val downloadService = DownloadService.getInstance(applicationContext)

            try {
                downloadService.download(issue, isAutomatically = true).join()
                log.debug("successfully downloaded")

                while (!issue.isDownloaded(applicationContext)) {
                    log.debug("delaying")
                    delay(1000)
                }

                Result.success()
            } catch (e: Exception) {
                log.debug("download failed")
                Sentry.captureException(e)
                Result.retry()
            }
        } ?: run {
            Sentry.captureMessage("download failed - getLastIssues returned null")
            Result.failure()
        }
    }
}
