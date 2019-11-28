package de.taz.app.android.api.models

import androidx.room.Entity
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(
    tableName = "Issue",
    primaryKeys = ["feedName", "date"]
)
data class IssueStub(
    override val feedName: String,
    override val date: String,
    val key: String? = null,
    override val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val fileList: List<String>,
    val fileListPdf: List<String> = emptyList()
): IssueOperations {

    constructor(issue: Issue): this (
        issue.feedName, issue.date, issue.key, issue.baseUrl, issue.status,
        issue.minResourceVersion, issue.zipName, issue.zipPdfName,
        issue.navButton, issue.fileList, issue.fileListPdf
    )

    suspend fun getIssue(): Issue {
        return withContext(Dispatchers.IO) {
            IssueRepository.getInstance().getIssue(this@IssueStub)
        }
    }
}