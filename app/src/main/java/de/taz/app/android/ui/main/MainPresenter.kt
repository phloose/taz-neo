package de.taz.app.android.ui.main

import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.archive.ArchiveFragment
import de.taz.app.android.ui.drawer.bookmarks.BookmarkDrawerFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.login.LoginFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainPresenter: MainContract.Presenter, BasePresenter<MainContract.View, MainDataController>(
    MainDataController::class.java
) {

    override fun onViewCreated() {
        getView()?.apply {
            getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                IssueRepository.getInstance().getLatestIssue()?.let { issue ->
                    viewModel?.setIssue(issue)
                }
            }
            showArchive()
        }
    }


    override fun onItemClicked(imageView: ImageView) {
        getView()?.let {

            when (imageView.id) {
                R.id.drawer_icon_content -> {
                    it.highlightDrawerIcon(imageView)
                    it.setDrawerTitle(R.string.navigation_drawer_icon_content)
                    it.showDrawerFragment(SectionDrawerFragment())
                }
                R.id.drawer_icon_home -> {
                    it.setDrawerTitle(R.string.navigation_drawer_icon_home)
                    it.showArchive()
                    it.closeDrawer()
                }
                R.id.drawer_icon_bookmarks -> {
                    it.highlightDrawerIcon(imageView)
                    it.setDrawerTitle(R.string.navigation_drawer_icon_bookmarks)
                    it.showDrawerFragment(BookmarkDrawerFragment())
                }
                R.id.drawer_icon_settings -> {
                    it.highlightDrawerIcon(imageView)
                    it.setDrawerTitle(R.string.navigation_drawer_icon_settings)
                    it.showMainFragment(LoginFragment())
                }
                R.id.drawer_icon_help -> {
                    it.highlightDrawerIcon(imageView)
                    it.setDrawerTitle(R.string.navigation_drawer_icon_help)
                    // TODO
                    it.showToast("should show help")
                }
            }
        }
    }

}