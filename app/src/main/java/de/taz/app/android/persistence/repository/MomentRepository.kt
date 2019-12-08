package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.UiThread
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueMomentJoin
import de.taz.app.android.util.SingletonHolder

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    @UiThread
    fun save(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        appDatabase.runInTransaction {
            appDatabase.fileEntryDao().insertOrReplace(moment.imageList)
            appDatabase.issueMomentJoinDao().insertOrReplace(
                moment.imageList.mapIndexed { index, fileEntry ->
                    IssueMomentJoin(issueFeedName, issueDate, issueStatus, fileEntry.name, index)
                }
            )
        }
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment {
        return Moment(
            appDatabase.issueMomentJoinDao().getMomentFiles(
                issueFeedName,
                issueDate,
                issueStatus
            )
        )
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(issueOperations: IssueOperations): Moment {
        return getOrThrow(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    @UiThread
    fun get(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment? {
        return try {
            getOrThrow(issueFeedName, issueDate, issueStatus)
        } catch (e: NotFoundException) {
            null
        }
    }

    @UiThread
    fun get(issueOperations: IssueOperations): Moment? {
        return get(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    @UiThread
    fun delete(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        appDatabase.runInTransaction {
            appDatabase.issueMomentJoinDao().delete(
                moment.imageList.mapIndexed { index, fileEntry ->
                    IssueMomentJoin(issueFeedName, issueDate, issueStatus, fileEntry.name, index)
                }
            )
            try {
                appDatabase.fileEntryDao().delete(moment.imageList)
            } catch (e: SQLiteConstraintException) {
                // do not delete is used by another issue
            }
        }
    }
}