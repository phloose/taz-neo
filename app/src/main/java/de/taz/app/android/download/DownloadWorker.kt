package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.awaitCallback
import de.taz.app.android.util.okHttpClient
import de.taz.app.android.util.runIfNotNull
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request


/**
 * Helper Object used by [WorkManagerDownloadWorker] and [DownloadService] to download
 */

class DownloadWorker(
    private val httpClient: OkHttpClient,
    private val downloadRepository: DownloadRepository,
    private val fileEntryRepository: FileEntryRepository,
    private val fileHelper: FileHelper,
    private val workManager: WorkManager
) {

    constructor(applicationContext: Context) : this(
        okHttpClient(applicationContext),
        DownloadRepository.getInstance(applicationContext),
        FileEntryRepository.getInstance(applicationContext),
        FileHelper.createInstance(applicationContext),
        WorkManager.getInstance(applicationContext)
    )

    private val log by Log

    /**
     * start download of given files/downloads
     * @param fileEntries - [FileEntryOperations] to download
     * @param fileNames - [FileEntryOperations.name] of files to download
     * @param downloads - [Download]s to download
     */
    suspend fun startDownloads(
        fileEntries: List<FileEntryOperations>? = null,
        fileNames: List<String>? = null,
        downloads: List<Download>? = null
    ): List<Job> {
        val jobs = mutableListOf<Job>()
        jobs.addAll(fileNames?.map {
            startDownload(it)
        } ?: emptyList())
        jobs.addAll(downloads?.map {
            startDownload(it.file.name)
        } ?: emptyList())
        jobs.addAll(fileEntries?.map {
            startDownload(it.name)
        } ?: emptyList())
        return  jobs
    }

    /**
     * start download
     * @param fileName - [FileEntryOperations.name] of [FileEntryOperations] to download
     */
    suspend fun startDownload(fileName: String): Job = CoroutineScope(Dispatchers.IO).launch {
        fileEntryRepository.get(fileName)?.let { fileEntry ->
            downloadRepository.getStub(fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (fromDB.lastSha256 != fileEntry.sha256 || fromDB.status !in arrayOf(
                        DownloadStatus.done,
                        DownloadStatus.started,
                        DownloadStatus.takeOld
                    )
                ) {
                    log.debug("starting download of ${fromDB.fileName}")

                    downloadRepository.setStatus(fromDB, DownloadStatus.started)

                    try {
                        val response = awaitCallback(
                            httpClient.newCall(
                                Request.Builder().url(fromDB.url).get().build()
                            )::enqueue
                        )

                        if (response.code.toString().startsWith("2")) {
                            @Suppress("NAME_SHADOWING")
                            response.body?.source()?.let { source ->
                                // ensure folders are created
                                fileHelper.createFileDirs(fileEntry)
                                val sha256 = fileHelper.writeFile(fileEntry, source)
                                if (sha256 == fileEntry.sha256) {
                                    log.debug("sha256 matched for file ${fromDB.fileName}")
                                    downloadRepository.saveLastSha256(fromDB, sha256)
                                    downloadRepository.setStatus(fromDB, DownloadStatus.done)
                                    log.debug("finished download of ${fromDB.fileName}")
                                } else {
                                    val m = "sha256 did NOT match the one of ${fromDB.fileName}"
                                    log.warn(m)
                                    Sentry.capture(m)
                                    if (fileHelper.getFile(fileName)!!.exists()) {
                                        downloadRepository.setStatus(fromDB, DownloadStatus.takeOld)
                                    } else {
                                        downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                                    }
                                }
                            } ?: run {
                                log.debug("aborted download of ${fromDB.fileName} - file is empty")
                                downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                            }
                        } else {
                            log.warn("Download was not successful ${response.code}")
                            downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                            Sentry.capture(response.message)
                        }
                    } catch (e: Exception) {
                        log.warn("aborted download of ${fromDB.fileName} - ${e.localizedMessage}")
                        downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                        Sentry.capture(e)
                    }
                } else {
                    log.debug("skipping download of ${fromDB.fileName} - already downloading/ed")
                }

                // cancel workmanager request if downloaded successfully
                if (fromDB.status == DownloadStatus.done) {
                    fromDB.workerManagerId?.let {
                        workManager.cancelWorkById(it)
                        log.info("canceling WorkerManagerRequest for ${fromDB.fileName}")
                    }
                    fromDB.workerManagerId = null
                    downloadRepository.update(fromDB)
                }
            } ?: log.error("download for $fileName failed. File not found in downloadRepository")
        }
    }
}

class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result = coroutineScope {

        val issueFeedName = inputData.getString(DATA_ISSUE_FEEDNAME)
        val issueDate = inputData.getString(DATA_ISSUE_DATE)
        val issueStatus = inputData.getString(DATA_ISSUE_STATUS)?.let { IssueStatus.valueOf(it) }

        log.debug("starting to download - issueDate: $issueDate")

        runIfNotNull(issueFeedName, issueDate, issueStatus) { feedName, date, status ->
            val downloadService = DownloadService.getInstance(applicationContext)
            val issueRepository = IssueRepository.getInstance(applicationContext)

            return@runIfNotNull async {
                try {
                    issueRepository.getIssue(
                        feedName, date, status
                    )?.let { issue ->
                        downloadService.download(issue).join()
                        log.debug("successfully downloaded")
                        Result.success()
                    } ?: Result.failure()
                } catch (e: Exception) {
                    Sentry.capture(e)
                    log.debug("download failed")
                    Result.failure()
                }
            }
        }?.await() ?: run {
            log.debug("download failed")
            Result.failure()
        }
    }
}
