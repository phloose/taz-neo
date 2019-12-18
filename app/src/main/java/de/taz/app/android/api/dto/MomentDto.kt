package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MomentDto(
    val imageList: List<FileEntryDto>? = null
)