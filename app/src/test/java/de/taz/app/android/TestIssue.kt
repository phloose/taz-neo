package de.taz.app.android

import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.Moment

val testIssue = Issue(
    "taz",
    "2019-10-17",
    Moment(),
    null,
    "https://example.com",
    IssueStatus.regular,
    23,
    null,
    null,
    null,
    null,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList()
)
