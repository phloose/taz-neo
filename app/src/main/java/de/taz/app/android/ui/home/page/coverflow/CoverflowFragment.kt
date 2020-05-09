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
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.util.Log
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

    private var issueStubToSkipTo: IssueStub? = null

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

        issueStubToSkipTo?.let { issueStubToSkipTo ->
            if (issueStubs.indexOfFirst { it.key == issueStubToSkipTo.key } >= 0) {
                skipToItem(issueStubToSkipTo)
            }
        }
    }

    override fun setAuthStatus(authStatus: AuthStatus) {
        coverFlowPagerAdapter.setAuthStatus(authStatus)
        getCurrentPosition()?.let {
            skipToPosition(it)
        }
    }

    override fun setFeeds(feeds: List<Feed>) {
        coverFlowPagerAdapter.setFeeds(feeds)
        getCurrentPosition()?.let {
            skipToPosition(it)
        }
    }

    override fun setInactiveFeedNames(feedNames: Set<String>) {
        coverFlowPagerAdapter.setInactiveFeedNames(feedNames)
        getCurrentPosition()?.let {
            skipToPosition(it)
        }
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

    fun skipToItem(issueStub: IssueStub) {
        val position = coverFlowPagerAdapter.getPosition(issueStub)
        if (position >= 0) {
            issueStubToSkipTo = null
            skipToPosition(position)
        } else {
            issueStubToSkipTo = issueStub
        }
    }

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
                setCurrentPosition(position)
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
}
