package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"
const val POSITION = "position"
const val SECTION_KEY = "sectionKey"

class SectionPagerFragment : BaseViewModelFragment<SectionPagerViewModel>(
    R.layout.fragment_webview_pager
) {
    override val enableSideBar: Boolean = true

    private var sectionAdapter: SectionPagerAdapter? = null

    private var sectionKey: String? = null
    private var issueFeedName: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_section

    companion object {
        fun createInstance(sectionFileName: String): SectionPagerFragment {
            val fragment = SectionPagerFragment()
            fragment.sectionKey = sectionFileName
            return fragment
        }

        fun createInstance(issueStub: IssueStub): SectionPagerFragment {
            val fragment = SectionPagerFragment()
            fragment.issueFeedName = issueStub.feedName
            fragment.issueDate = issueStub.date
            fragment.issueStatus = issueStub.status
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            issueDate = getString(ISSUE_DATE)
            issueFeedName = getString(ISSUE_FEED)
            try {
                issueStatus = getString(ISSUE_STATUS)?.let { IssueStatus.valueOf(it) }
            } catch (e: IllegalArgumentException) {
                // do nothing issueStatus is null
            }
            sectionKey = getString(SECTION_KEY)
            viewModel.currentPositionLiveData.value = getInt(POSITION, 0)
        }
        viewModel.issueOperationsLiveData.observe(this, object : Observer<IssueOperations?> {
            override fun onChanged(t: IssueOperations?) {
                t?.let {
                    viewModel.issueOperationsLiveData.removeObserver(this)
                    updateAndDownloadIssue(t)
                }
            }
        })
    }

    /**
     * start download of issue - if the issue is not downloaded yet check whether the metadata has
     * changed - if yes persist and start download for updated issue
     */
    private fun updateAndDownloadIssue(issueOperations: IssueOperations) =
        CoroutineScope(Dispatchers.IO).launch {
            val issueRepository = IssueRepository.getInstance(context?.applicationContext)
            issueRepository.getIssue(issueOperations)?.let {
                if (!it.isDownloaded(context?.applicationContext)) {
                    val apiService = ApiService.getInstance(context?.applicationContext)
                    val downloadService = DownloadService.getInstance(context?.applicationContext)

                    // start download
                    downloadService.download(it)

                    // check whether the metadata has changed online
                    val updateIssue = apiService.getIssueByFeedAndDateAsync(
                        it.feedName, it.date
                    ).await()
                    // if it has changed - save and redownload issue
                    if (it != updateIssue && updateIssue != null) {
                        issueRepository.save(updateIssue)
                        downloadService.download(updateIssue)
                    }
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sectionKey?.let {
            viewModel.sectionKeyLiveData.value = it
        }
        runIfNotNull(issueFeedName, issueDate, issueStatus) { feedName, date, status ->
            viewModel.apply {
                issueFeedNameLiveData.value = feedName
                issueDateLiveData.value = date
                issueStatusLiveData.value = status
            }
        }

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        viewModel.currentPositionLiveData.observeDistinct(this) {
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        viewModel.sectionStubListLiveData.observeDistinct(this) { sectionStubList ->
            runIfNotNull(
                sectionStubList,
                viewModel.currentPosition
            ) { _, currentPosition ->
                webview_pager_viewpager.apply {
                    adapter?.notifyDataSetChanged()
                    setCurrentItem(currentPosition, false)
                }
                loading_screen?.visibility = View.GONE
            }
        }

        viewModel.issueOperationsLiveData.observeDistinct(this) { issueOperations ->
            issueOperations?.let { setDrawerIssue(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }

    fun tryLoadSection(sectionFileName: String): Boolean {
        viewModel.sectionStubListLiveData.value?.indexOfFirst { it.key == sectionFileName }?.let {
            if (it >= 0) {
                if (viewModel.currentPosition != it) {
                    lifecycleScope.launchWhenResumed {
                        webview_pager_viewpager.setCurrentItem(it, false)
                    }
                }
                return true
            }
        }
        return false
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            if (adapter == null) {
                sectionAdapter = SectionPagerAdapter()
                adapter = sectionAdapter
            }
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            registerOnPageChangeCallback(pageChangeListener)
            offscreenPageLimit = 2
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.currentPositionLiveData.value = position
            getMainView()?.setActiveDrawerSection(position)
            sectionAdapter?.getSectionStub(position)?.let {
                lifecycleScope.launchWhenResumed {
                    val navButton = it.getNavButton()
                    showNavButton(navButton)
                }
            }
        }
    }

    override fun onStop() {
        webview_pager_viewpager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    private inner class SectionPagerAdapter : FragmentStateAdapter(this@SectionPagerFragment) {

        private val sectionStubs: List<SectionStub>
            get() = viewModel.sectionStubListLiveData.value ?: emptyList()

        override fun createFragment(position: Int): Fragment {
            val section = sectionStubs[position]
            return SectionWebViewFragment.createInstance(section)
        }

        override fun getItemCount(): Int = sectionStubs.size

        fun getSectionStub(position: Int): SectionStub {
            return sectionStubs[position]
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_DATE, issueDate ?: viewModel.issueDate)
        outState.putString(ISSUE_FEED, issueFeedName ?: viewModel.issueFeedName)
        outState.putString(
            ISSUE_STATUS,
            issueStatus?.toString() ?: viewModel.issueStatus?.toString()
        )
        outState.putString(SECTION_KEY, viewModel.sectionKey)
        viewModel.currentPosition?.let {
            outState.putInt(POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome(skipToNewestIssue = true)
            }
            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }
}