package de.taz.app.android.util

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import de.taz.app.android.api.models.Download
import de.taz.app.android.persistence.repository.DownloadRepository
import kotlinx.io.IOException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class FileHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<FileHelper, Context>(::FileHelper)

    private val downloadRepository = DownloadRepository.getInstance(applicationContext)

    fun deleteFile(fileName: String): Boolean {
        return downloadRepository.get(fileName)?.let { download ->
            deleteFileForDownload(download)
        } ?: false
    }

    fun deleteFileForDownload(download: Download): Boolean {
        return getFile(download.path).delete()
    }

    fun deleteFileFromFileSystem(filePath: String) : Boolean {
        val file = File(filePath)
        return file.delete()
    }

    fun getFile(fileName: String, internal: Boolean = false): File {
        // TODO read from settings where to save
        // TODO notification if external not writable?
        return if (internal || !isExternalStorageWritable())
            File(applicationContext.filesDir, fileName)
        else {
            return File(ContextCompat.getExternalFilesDirs(applicationContext, null).first(), fileName)
        }
    }

    fun getFileDirectoryUrl(context: Context, internal: Boolean = false) : String {
        context.applicationContext.let {
            return if (internal)
                "file://${it.filesDir.absolutePath}"
            else
                "file://${ContextCompat.getExternalFilesDirs(it,null).first().absolutePath}"
        }

    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    @Throws(IOException::class)
    fun readFileFromAssets(path: String): String {
        var bufferedReader: BufferedReader? = null
        var data = ""
        try {
            bufferedReader = BufferedReader(
                InputStreamReader(
                    applicationContext.assets.open(path),
                    "UTF-8"
                )
            )

            var line: String? = bufferedReader.readLine()
            while (line != null) {
                data += line
                line = bufferedReader.readLine()
            }
        } finally {
            bufferedReader?.close()
        }
        return data
    }
}