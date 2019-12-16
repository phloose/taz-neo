package de.taz.app.android.ui.webview

import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.Shareable
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticleWebViewPresenter(
    apiService: ApiService = ApiService.getInstance(),
    resourceInfoRepository: ResourceInfoRepository = ResourceInfoRepository.getInstance()
) : WebViewPresenter<Article>(apiService, resourceInfoRepository) {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showHome()

            R.id.bottom_navigation_action_bookmark ->
                getView()?.apply {
                    if (isBottomSheetVisible()) {
                        hideBottomSheet()
                    } else {
                        showBookmarkBottomSheet()
                    }
                }

            R.id.bottom_navigation_action_share ->
                if (webViewDisplayable is Shareable) {
                    webViewDisplayable.getLink()?.let {
                        getView()?.apply {
                            shareText(it)
                            setIconInactive(R.id.bottom_navigation_action_share)
                        }
                    }
                }

            R.id.bottom_navigation_action_size ->
                if (activated) {
                    getView()?.showFontSettingBottomSheet()
                } else {
                    getView()?.hideBottomSheet()
                }
        }
    }

    fun showSection() {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        getView()?.getMainView()?.let {
            it.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                webViewDisplayable?.getSection()?.let { section ->
                    it.showInWebView(section)
                }
            }
        }
    }

}