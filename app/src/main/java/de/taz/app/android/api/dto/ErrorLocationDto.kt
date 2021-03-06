package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ErrorLocationDto(
    val line: Int? = null,
    val column: Int? = null
)
