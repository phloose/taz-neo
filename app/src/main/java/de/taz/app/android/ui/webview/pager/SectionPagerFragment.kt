package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen

class SectionPagerFragment : BaseMainFragment(
    R.layout.fragment_webview_pager
) {
    private val log by Log

    override val enableSideBar: Boolean = true
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_section

    private val issueContentViewModel: IssueContentViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueContentViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
        setupViewPager()

        loading_screen?.visibility = View.GONE


        issueContentViewModel.sectionListLiveData.observeDistinct(this.viewLifecycleOwner) { sectionStubs ->
            webview_pager_viewpager.adapter = SectionPagerAdapter(sectionStubs)
            tryScrollToSection()
        }

        issueContentViewModel.displayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) {
            tryScrollToSection()
        }
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            registerOnPageChangeCallback(pageChangeListener)
            offscreenPageLimit = 2
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        override fun onPageSelected(position: Int) {
            if (lastPage != null && lastPage != position) {
                runIfNotNull(
                    issueContentViewModel.issueStubAndDisplayableKeyLiveData.value?.first,
                    getCurrentSectionStub()
                ) { issueStub, displayable ->
                    if (issueContentViewModel.activeDisplayMode.value == IssueContentDisplayMode.Section) {
                        issueContentViewModel.setDisplayable(issueStub.issueKey, displayable.key)
                    }
                }
            }
            lastPage = position

            lifecycleScope.launchWhenResumed {
                getCurrentSectionStub()?.getNavButton()?.let {
                    showNavButton(it)
                }
            }

        }
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

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }

    override fun onStop() {
        webview_pager_viewpager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    private inner class SectionPagerAdapter(val sectionStubs: List<SectionStub>) :
        FragmentStateAdapter(this@SectionPagerFragment) {

        override fun createFragment(position: Int): Fragment {
            val sectionStub = sectionStubs[position]
            return SectionWebViewFragment.createInstance(sectionStub.sectionFileName)
        }

        override fun getItemCount(): Int = sectionStubs.size
    }

    private fun tryScrollToSection() {
        val displayableKey = issueContentViewModel.displayableKeyLiveData.value
        if (displayableKey?.startsWith("sec") == true) {
            log.debug("Section selected: $displayableKey")
            issueContentViewModel.lastSectionKey = displayableKey
            issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Section)

            getSupposedPagerPosition()?.let {
                if (it >= 0 && it != getCurrentPagerPosition()) {
                    webview_pager_viewpager.setCurrentItem(it, false)
                }
            }

        }
    }

    private fun getCurrentPagerPosition(): Int? {
        return webview_pager_viewpager?.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position =
            (webview_pager_viewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.indexOfFirst {
                it.key == issueContentViewModel.displayableKeyLiveData.value
            }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentSectionStub(): SectionStub? {
        return getCurrentPagerPosition()?.let {
            issueContentViewModel.sectionListLiveData.value?.get(it)
        }
    }
}