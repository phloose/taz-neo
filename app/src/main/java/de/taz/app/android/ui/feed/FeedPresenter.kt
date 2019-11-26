package de.taz.app.android.ui.feed

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.settings.SettingsOuterFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedPresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val feedRepository: FeedRepository = FeedRepository.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : BasePresenter<FeedFragment, FeedDataController>(FeedDataController::class.java), FeedContract.Presenter {

    override suspend fun onRefresh() {
        withContext(Dispatchers.IO) {
            try {
                feedRepository.save(apiService.getFeeds())
                issueRepository.save(apiService.getIssuesByDate())
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                getView()?.getMainView()?.showToast(R.string.toast_no_internet)
            } catch (e: ApiService.ApiServiceException.InsufficientDataException) {
                getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
            } catch (e: ApiService.ApiServiceException.WrongDataException) {
                getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> getView()?.getMainView()?.showMainFragment(BookmarksFragment())
            R.id.bottom_navigation_action_settings -> getView()?.getMainView()?.showMainFragment(SettingsOuterFragment())
        }
    }
}