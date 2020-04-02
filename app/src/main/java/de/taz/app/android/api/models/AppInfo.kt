package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "AppInfo")
data class AppInfo (
    @PrimaryKey val appName: AppName,
    val globalBaseUrl: String,
    val appType: AppType,
    val androidVersion: Int
) {
    constructor(productDto: ProductDto): this(
        productDto.appName!!,
        productDto.globalBaseUrl!!,
        productDto.appType!!,
        productDto.androidVersion!!
    )

    companion object {
        private val log by Log

        suspend fun update(): AppInfo? = withContext(Dispatchers.IO) {
            try {
                ApiService.getInstance().getAppInfo()?.let {
                    AppInfoRepository.getInstance().save(it)
                    log.info("Initialized AppInfo")
                    return@withContext it
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                null
            }
        }
    }
}
