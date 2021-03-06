package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SectionDto (
    val sectionHtml: FileEntryDto,
    val title: String,
    val type: SectionType,
    val articleList: List<ArticleDto>? = null,
    val imageList: List<ImageDto>? = null,
    val extendedTitle: String? = null,
    val navButton: ImageDto
)

@JsonClass(generateAdapter = false)
enum class SectionType {
    articles
}