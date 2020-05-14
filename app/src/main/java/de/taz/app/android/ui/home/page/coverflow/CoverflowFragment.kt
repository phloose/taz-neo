package de.taz.app.android.ui.home.page.coverflow


import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.webview.pager.ISSUE_DATE
import de.taz.app.android.ui.webview.pager.ISSUE_FEED
import de.taz.app.android.ui.webview.pager.ISSUE_STATUS
import de.taz.app.android.ui.webview.pager.POSITION
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*


class CoverflowFragment : HomePageFragment(R.layout.fragment_coverflow) {

    val log by Log

    private val openDatePicker: (Date) -> Unit = { issueDate ->
        showBottomSheet(DatePickerFragment.create(this, issueDate))
    }

    val coverFlowPagerAdapter = CoverflowAdapter(
        this@CoverflowFragment,
        R.layout.fragment_cover_flow_item,
        openDatePicker
    )
    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null
    private var issueFeedname: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            runIfNotNull(
                getString(ISSUE_DATE),
                getString(ISSUE_FEED),
                getString(ISSUE_STATUS)
            ) { date, feed, status ->
                issueDate = date
                issueFeedname = feed
                issueStatus = IssueStatus.valueOf(status)
            } ?: run {
                setCurrentPosition(getInt(POSITION, RecyclerView.NO_POSITION))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragment_cover_flow_grid.apply {
            context?.let { context ->
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            adapter = coverFlowPagerAdapter

            snapHelper.apply {
                attachToRecyclerView(fragment_cover_flow_grid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true
            }

        }

        fragment_cover_flow_to_archive.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem += 1
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fragment_cover_flow_grid.apply {
            addOnScrollListener(onScrollListener)
        }
        getMainView()?.apply {
            setDrawerNavButton()
            setActiveDrawerSection(RecyclerView.NO_POSITION)
            changeDrawerIssue()
        }
    }

    override fun onPause() {
        fragment_cover_flow_grid.apply {
            removeOnScrollListener(onScrollListener)
        }
        super.onPause()
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        coverFlowPagerAdapter.setIssueStubs(issueStubs.reversed())

        runIfNotNull(issueFeedname, issueDate, issueStatus) { feed, date, status ->
            skipToItem(feed, date, status)
        }
    }

    override fun setAuthStatus(authStatus: AuthStatus) {
        coverFlowPagerAdapter.setAuthStatus(authStatus)
        skipToPosition(getCurrentPosition())
    }

    override fun setFeeds(feeds: List<Feed>) {
        coverFlowPagerAdapter.setFeeds(feeds)
        skipToPosition(getCurrentPosition())
    }

    override fun setInactiveFeedNames(feedNames: Set<String>) {
        coverFlowPagerAdapter.setInactiveFeedNames(feedNames)
        skipToPosition(getCurrentPosition())
    }

    fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    fun skipToEnd() {
        fragment_cover_flow_grid.apply {
            scrollToPosition(coverFlowPagerAdapter.itemCount.minus(1))
            smoothScrollBy(1, 0)
        }
    }

    fun skipToCurrentItem() {
        runIfNotNull(issueFeedname, issueDate, issueStatus) { feed, date, status ->
            val position = coverFlowPagerAdapter.getPosition(feed, date, status)
            if (position >= 0) {
                skipToPosition(position)
            }
        }
    }

    fun skipToItem(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        this.issueFeedname = issueFeedName
        this.issueDate = issueDate
        this.issueStatus = issueStatus
        skipToCurrentItem()
    }

    fun skipToItem(issueStub: IssueStub) =
        skipToItem(issueStub.feedName, issueStub.date, issueStub.status)

    fun skipToPosition(position: Int) = activity?.runOnUiThread {
        fragment_cover_flow_grid.apply {
            scrollToPosition(position)
            smoothScrollBy(1, 0)
        }
    }

    inner class OnScrollListener : RecyclerView.OnScrollListener() {

        private var isDragEvent = false

        override fun onScrollStateChanged(
            recyclerView: RecyclerView,
            newState: Int
        ) {
            // if user is dragging to left if no newer issue -> refresh
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_SETTLING && isDragEvent &&
                !recyclerView.canScrollHorizontally(1)
            ) {
                activity?.findViewById<SwipeRefreshLayout>(R.id.coverflow_refresh_layout)
                    ?.setRefreshingWithCallback(true)
            }
            isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
        }


        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val position: Int =
                (layoutManager.findFirstVisibleItemPosition() + layoutManager.findLastVisibleItemPosition()) / 2

            // transform the visible children visually
            (fragment_cover_flow_grid as? ViewGroup)?.apply {
                children.forEach { child ->
                    val childPosition = (child.left + child.right) / 2f
                    val center = width / 2

                    ZoomPageTransformer.transformPage(child, (center - childPosition) / width)
                }
            }

            // persist position and download new issues if user is scrolling
            if (position >= 0) {
                setCurrentPosition(position, coverFlowPagerAdapter.getItem(position))
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val visibleItemCount = 3
                    if (position < 2 * visibleItemCount) {
                        coverFlowPagerAdapter.getItem(0)?.date?.let { requestDate ->
                            getNextIssueMoments(requestDate)
                        }
                    }
                }
            }

            // if user swiped update the drawer issue
            if (dx != 0) {
                getMainView()?.apply {
                    coverFlowPagerAdapter.getItem(position)?.let {
                        setDrawerIssue(it)
                        // if drawer is visible update UI
                        if (getMainView()?.isDrawerVisible(Gravity.START) == true) {
                            changeDrawerIssue()
                        }
                    }
                }
            }
        }
    }

    fun setCurrentPosition(position: Int, issueOperations: IssueOperations?) {
        issueOperations?.let {
            issueFeedname = issueOperations.feedName
            issueStatus = issueOperations.status
            issueDate = issueOperations.date
        }
        setCurrentPosition(position)
    }

    fun hasSetItem(): Boolean {
        return runIfNotNull(issueFeedname, issueDate, issueStatus) { _, _, _ ->
            true
        } ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(POSITION, getCurrentPosition())
        outState.putString(ISSUE_FEED, issueFeedname)
        outState.putString(ISSUE_STATUS, issueStatus?.toString())
        outState.putString(ISSUE_DATE, issueDate)
        super.onSaveInstanceState(outState)
    }

}
