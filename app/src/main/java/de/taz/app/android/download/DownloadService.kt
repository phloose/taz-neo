package de.taz.app.android.download

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.work.*
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import io.sentry.core.Sentry
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

const val CONCURRENT_DOWNLOAD_LIMIT = 10
const val SHA_EMPTY_STRING = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

@Mockable
class DownloadService private constructor(val applicationContext: Context) {

    companion object : SingletonHolder<DownloadService, Context>(::DownloadService)

    private val apiService = ApiService.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val appInfoRepository = AppInfoRepository.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val downloadRepository = DownloadRepository.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)
    val issueRepository = IssueRepository.getInstance(applicationContext)
    private val serverConnectionHelper = ServerConnectionHelper.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

    private var appInfo: AppInfo? = null
    private val resourceInfo
        get() = resourceInfoRepository.getNewest()

    private val httpClient = OkHttp.client

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val downloadList = ConcurrentLinkedDeque<Download>()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val currentDownloads = AtomicInteger(0)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val currentDownloadList = ConcurrentLinkedQueue<String>()

    private val tagJobMap = ConcurrentHashMap<String, ConcurrentLinkedQueue<Job>>()

    init {
        Transformations.distinctUntilChanged(serverConnectionHelper.isDownloadServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected) {
                    startDownloadsIfCapacity()
                }
            }
    }

    /**
     * start download for cacheableDownload
     * @param cacheableDownload - [CacheableDownload] to download
     * @param baseUrl - [String] providing the baseUrl - only necessary for downloads
     *                  where the baseUrl can not be automatically calculated (mostly [FileEntry])
     */
    fun download(
        cacheableDownload: CacheableDownload,
        baseUrl: String? = null,
        isAutomatically: Boolean = false
    ): Job =
        CoroutineScope(Dispatchers.IO).launch {
            val start = DateHelper.now
            var redoJob: Job? = null
            if (!cacheableDownload.isDownloaded(applicationContext)) {
                ensureAppInfo()

                val issue = cacheableDownload as? Issue
                var downloadId: String? = null

                cacheableDownload.setDownloadStatus(DownloadStatus.started)

                // if we download an issue tell the server we start downloading it
                issue?.let {
                    downloadId = try {
                        apiService.notifyServerOfDownloadStart(
                            issue.feedName,
                            issue.date,
                            isAutomatically
                        )
                    } catch (nie: ApiService.ApiServiceException) {
                        null
                    }
                    issueRepository.setDownloadDate(it, Date())
                    redoJob = launch {
                        // check if metadata has changed and update db and restart download
                        val fromServer = apiService.getIssueByFeedAndDateAsync(
                            issue.feedName, issue.date
                        ).await()
                        if (
                            fromServer?.status == issue.status && fromServer.moTime != issue.moTime
                        ) {
                            cancelDownloadsForTag(issue.tag)
                            issueRepository.save(fromServer)
                            download(fromServer).join()
                        }
                    }
                }

                // wait for [CacheableDownload]'s files to be downloaded
                val isDownloadedLiveData = Transformations.distinctUntilChanged(
                    DownloadRepository.getInstance()
                        .isDownloadedLiveData(cacheableDownload.getAllFileNames())
                )
                val observer = object : Observer<Boolean> {
                    override fun onChanged(t: Boolean?) {
                        if (t == true) {
                            isDownloadedLiveData.removeObserver(this)
                            CoroutineScope(Dispatchers.IO).launch {
                                // mark download as downloaded - if it is an issue including articles etc
                                issue?.setDownloadStatusIncludingChildren(DownloadStatus.done)
                                    ?: run {
                                        cacheableDownload.setDownloadStatus(DownloadStatus.done)
                                    }
                                log.debug("download of ${cacheableDownload::class.java} complete in ${DateHelper.now - start}")
                                // notify server of completed download
                                downloadId?.let { downloadId ->
                                    val seconds: Float =
                                        (System.currentTimeMillis() - start) / 1000f
                                    apiService.notifyServerOfDownloadStopAsync(
                                        downloadId,
                                        seconds
                                    ).await()
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { isDownloadedLiveData.observeForever(observer) }

                // create Downloads
                addDownloadsToDownloadList(cacheableDownload, baseUrl)
                redoJob?.join()
            }
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun startDownloadsIfCapacity() {
        while (serverConnectionHelper.isDownloadServerReachable && currentDownloads.get() < CONCURRENT_DOWNLOAD_LIMIT && downloadList.size > 0) {
            startDownloadIfCapacity() ?: break
        }
    }

    /**
     * start downloads if there are less then [CONCURRENT_DOWNLOAD_LIMIT] downloads started atm
     * and the server is reachable
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun startDownloadIfCapacity(): Job? {
        if (serverConnectionHelper.isDownloadServerReachable && currentDownloads.getAndIncrement() < CONCURRENT_DOWNLOAD_LIMIT) {
            downloadList.pollFirst()?.let { download ->
                if (!currentDownloadList.contains(download.fileName)) {
                    currentDownloadList.offer(download.fileName)
                    val job = CoroutineScope(Dispatchers.IO).launch {
                        getFromServer(download)
                        log.info("download ${download.fileName} started")
                        startDownloadsIfCapacity()
                    }
                    download.tag?.let { tag ->
                        val jobsForTag = tagJobMap.getOrPut(tag) { ConcurrentLinkedQueue<Job>() }
                        jobsForTag.add(job)
                        tagJobMap[download.tag] = jobsForTag
                    }
                    job.invokeOnCompletion { cause ->
                        if (cause is CancellationException) {
                            DownloadService.log.info("download of ${download.fileName} has been canceled")
                            // cancellation was requested by program so do not retry
                            download.file.setDownloadStatus(DownloadStatus.pending)
                            downloadRepository.setStatus(download, DownloadStatus.pending)
                            currentDownloads.decrementAndGet()
                            currentDownloadList.remove(download.fileName)
                        }
                        download.tag?.let { tagJobMap[it]?.remove(job) }
                    }
                    return job
                }
            }
        }
        currentDownloads.decrementAndGet()
        return null
    }

    fun getBlockingFromServer(fileName: String) = runBlocking {
        downloadRepository.get(fileName)?.let {
            currentDownloads.incrementAndGet()
            getFromServer(it, true)
        }
    }

    /**
     * call server to get response
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun getFromServer(
        download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRestartDownload: Boolean = false
    ) {
        try {
            val fileEntry = download.file
            downloadRepository.getStub(download.fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (!(fromDB.lastSha256 == fileEntry.sha256 || fromDB.status in arrayOf(
                        DownloadStatus.done,
                        DownloadStatus.started
                    ))
                ) {
                    fileEntry.setDownloadStatus(DownloadStatus.started)
                    downloadRepository.setStatus(fromDB, DownloadStatus.started)

                    val response = awaitCallback(
                        httpClient.newCall(
                            Request.Builder().url(fromDB.url).get().build()
                        )::enqueue
                    )
                    handleResponse(response, download, doNotRestartDownload)
                } else {
                    log.debug("skipping download of ${fromDB.fileName} - already downloading/ed")
                    if (fromDB.lastSha256 == fileEntry.sha256) {
                        download.file.setDownloadStatus(DownloadStatus.done)
                        downloadRepository.setStatus(download, DownloadStatus.done)
                    }
                    currentDownloads.decrementAndGet()
                    currentDownloadList.remove(download.fileName)
                }
            }
        } catch (e: Exception) {
            when (e) {
                is UnknownHostException,
                is ConnectException -> {
                    serverConnectionHelper.isDownloadServerReachable = false
                    abortAndRetryDownload(download, doNotRestartDownload)
                    DownloadService.log.warn("aborted download of ${download.fileName} - ${e.localizedMessage}")
                }
                is SSLException,
                is IOException,
                is SSLHandshakeException,
                is SocketTimeoutException -> {
                    abortAndRetryDownload(download, doNotRestartDownload)
                    DownloadService.log.warn("aborted download of ${download.fileName} - ${e.localizedMessage}")
                }
                is CancellationException -> {
                    // do nothing will be caught by invokeOnCompletion
                    throw e
                }
                else -> {
                    DownloadService.log.warn("unknown error occurred - ${download.fileName}")
                    abortAndRetryDownload(download, doNotRestartDownload)
                    Sentry.captureException(e)
                    throw e
                }
            }
        }
    }

    /**
     * save server response to file, calculate sha and compare
     */
    private fun handleResponse(
        response: Response, download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRestartDownload: Boolean = false
    ) {
        val fileEntry = download.file
        if (response.isSuccessful) {
            response.body?.let { body ->
                // ensure folders are created
                fileHelper.createFileDirs(fileEntry)
                val sha256 = fileHelper.writeFile(fileEntry, body.source())
                downloadRepository.saveLastSha256(download, sha256)
                if (sha256 == fileEntry.sha256) {
                    downloadRepository.setStatus(download, DownloadStatus.done)
                    fileEntry.setDownloadStatus(DownloadStatus.done)
                    currentDownloads.decrementAndGet()
                    currentDownloadList.remove(download.fileName)
                } else {
                    if (sha256 == SHA_EMPTY_STRING) {
                        abortAndRetryDownload(download, doNotRestartDownload)
                    } else {
                        log.warn("sha256 did NOT match the one of ${download.fileName}")
                        fileEntry.setDownloadStatus(DownloadStatus.takeOld)
                        downloadRepository.setStatus(download, DownloadStatus.takeOld)
                        currentDownloads.decrementAndGet()
                        currentDownloadList.remove(download.fileName)
                    }
                }
                body.close()
            } ?: run {
                log.debug("aborted download of ${download.fileName} - file is empty")
                abortAndRetryDownload(download, doNotRestartDownload)
            }
        } else {
            log.warn("Download of ${download.fileName} not successful ${response.code}")
            Sentry.captureMessage(response.message)
            if (response.code in 400..499) {
                download.file.setDownloadStatus(DownloadStatus.failed)
                downloadRepository.setStatus(download, DownloadStatus.failed)
                currentDownloads.decrementAndGet()
                currentDownloadList.remove(download.fileName)
            } else if (response.code in 500..599) {
                serverConnectionHelper.isDownloadServerReachable = false
                abortAndRetryDownload(download, doNotRestartDownload)
            }
        }
    }

    /**
     * save Download and FileEntry as not downloaded in database then retry download
     * @param doNotRetry indicating whether to retry download - only for testing
     */
    private fun abortAndRetryDownload(
        download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRetry: Boolean = false
    ) {
        download.file.setDownloadStatus(DownloadStatus.aborted)
        downloadRepository.setStatus(download, DownloadStatus.aborted)
        currentDownloads.decrementAndGet()
        currentDownloadList.remove(download.fileName)
        if (!doNotRetry) {
            prependToDownloadList(download)
        }
    }

    /**
     * download new issue in background
     */
    fun scheduleNewestIssueDownload(tag: String): Operation? {
        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
                .setConstraints(getConstraints())
                .addTag(tag)

        return WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }

    /**
     * cancel all running download jobs
     */
    suspend fun cancelDownloadsForTag(tag: String) {
        log.debug("canceling downloads for tag: $tag")
        downloadList.removeAll(downloadList.filter { it.tag == tag })
        val jobsForTag = tagJobMap.remove(tag)
        jobsForTag?.forEach { it.cancelAndJoin() }
    }

    /**
     * cancel all issue downloads
     */
    suspend fun cancelIssueDownloads() {
        IssueRepository.getInstance(applicationContext).getDownloadStartedIssueStubs().forEach {
            cancelDownloadsForTag(it.tag)
        }
    }

    /**
     * create downloads, add them to the downloadList and start downloading
     * @param cacheableDownload [CacheableDownload] to create Downloads from
     * @param baseUrl - [String] providing the baseUrl - only necessary for downloads
     *                  where the baseUrl can not be automatically calculated (mostly [FileEntry])
     */
    private suspend fun addDownloadsToDownloadList(
        cacheableDownload: CacheableDownload,
        baseUrl: String? = null
    ) = withContext(Dispatchers.IO) {

        val tag = cacheableDownload.getDownloadTag()
        val issueOperations = cacheableDownload.getIssueOperations(applicationContext)

        // create Downloads and save them in the database
        cacheableDownload.getAllFileNames().forEach {
            fileEntryRepository.get(it)?.let { fileEntry ->
                if (fileEntry.downloadedStatus != DownloadStatus.done) {
                    val download: Download? =
                        if (baseUrl != null && cacheableDownload is FileEntry) {
                            createAndSaveDownload(baseUrl, fileEntry, tag)
                        } else {
                            when (fileEntry.storageType) {
                                StorageType.global -> {
                                    ensureAppInfo()
                                    appInfo?.globalBaseUrl?.let { globalBaseUrl ->
                                        createAndSaveDownload(globalBaseUrl, fileEntry, tag)
                                    }
                                }
                                StorageType.resource -> {
                                    resourceInfo?.resourceBaseUrl?.let { resourceBaseUrl ->
                                        createAndSaveDownload(resourceBaseUrl, fileEntry, tag)
                                    }
                                }
                                StorageType.issue -> {
                                    issueOperations?.baseUrl?.let { baseUrl ->
                                        createAndSaveDownload(baseUrl, fileEntry, tag)
                                    }
                                }
                                StorageType.public ->
                                    // TODO?
                                    null
                            }
                        }
                    download?.let {
                        if (!currentDownloadList.contains(download.fileName)
                            && !download.file.isDownloaded(applicationContext)
                        ) {
                            log.debug("adding ${download.fileName} to downloadList")
                            // issues are not shown immediately - so download other downloads like articles first
                            if (cacheableDownload is Issue) {
                                appendToDownloadList(download)
                            } else {
                                prependToDownloadList(download)
                            }
                        }
                    } ?: log.debug("creating download for ${fileEntry.name} returned null")
                }
            }
        }
    }

    private suspend fun ensureAppInfo() {
        if (appInfo == null) {
            AppInfo.get(applicationContext)
            appInfo = appInfoRepository.get()
        }
    }

    private fun appendToDownloadList(download: Download) {
        if (!currentDownloadList.contains(download.fileName) && !downloadList.contains(download)) {
            if (downloadList.offerLast(download)) {
                startDownloadsIfCapacity()
            }
        }
    }

    private fun prependToDownloadList(download: Download) {
        if (!currentDownloadList.contains(download.fileName)) {
            downloadList.removeAll(downloadList.filter { it.fileName == download.fileName })
            if (downloadList.offerFirst(download)) {
                startDownloadsIfCapacity()
            }
        }
    }

    /**
     * get Constraints for [WorkRequest] of [WorkManager]
     */
    private fun getConstraints(): Constraints {
        val onlyWifi: Boolean =
            applicationContext.getSharedPreferences(PREFERENCES_DOWNLOADS, Context.MODE_PRIVATE)
                ?.let {
                    SharedPreferenceBooleanLiveData(it, SETTINGS_DOWNLOAD_ONLY_WIFI, true).value
                } ?: true

        return Constraints.Builder()
            .setRequiredNetworkType(if (onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
    }

    /**
     * create [Download] for [FileEntry] and persist to DB
     * @param fileEntry - [FileEntry] to create Download of
     * @return created [Download]
     */
    private fun createAndSaveDownload(
        baseUrl: String,
        fileEntry: FileEntry,
        tag: String?
    ): Download? {
        val fromDB = downloadRepository.get(fileEntry.name)
        return if (fromDB == null) {
            val download = Download(
                baseUrl,
                fileEntry,
                tag = tag
            )
            if (downloadRepository.saveIfNotExists(download)) download else null
        } else {
            fromDB
        }
    }

    fun isDownloading(): Boolean {
        return currentDownloads.get() > 0
    }

}
