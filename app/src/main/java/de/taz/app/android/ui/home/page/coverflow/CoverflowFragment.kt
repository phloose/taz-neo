package de.taz.app.android.ui.home.page.coverflow


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CoverflowFragment : BaseMainFragment<CoverflowContract.Presenter>(), CoverflowContract.View {

    override val presenter = CoverflowPresenter()

    val log by Log

    private val coverFlowPagerAdapter = HomePageAdapter(
        this@CoverflowFragment,
        R.layout.fragment_cover_flow_item,
        presenter
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coverflow, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        coverflow_pager.apply {
            setPageTransformer(ZoomPageTransformer())
            overScrollMode = View.OVER_SCROLL_NEVER
            offscreenPageLimit = 3
            adapter = coverFlowPagerAdapter
            registerOnPageChangeCallback(CoverFlowOnPageChangeCallback())
        }
        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        val oldItemCount = coverFlowPagerAdapter.itemCount
        coverFlowPagerAdapter.apply {
            setIssueStubs(issueStubs.reversed())
        }
        if (oldItemCount == 0) {
            skipToEnd()
        }
    }

    override fun setAuthStatus(authStatus: AuthStatus) {
        coverFlowPagerAdapter.setAuthStatus(authStatus)
    }

    override fun setFeeds(feeds: List<Feed>) {
        coverFlowPagerAdapter.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        val oldItemCount = coverFlowPagerAdapter.itemCount
        coverFlowPagerAdapter.setInactiveFeedNames(inactiveFeedNames)
        if (oldItemCount == 0) {
            skipToEnd()
        }
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

    override fun skipToEnd() {
        coverflow_pager.adapter?.let {
            coverflow_pager.setCurrentItem(it.itemCount - 1, false)
        }
    }

    inner class CoverFlowOnPageChangeCallback : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

                getMainView()?.setDrawerIssue(coverFlowPagerAdapter.getItem(position))

                val visibleItemCount = 3
                val totalItemCount = coverFlowPagerAdapter.itemCount

                if (position > totalItemCount - 2 * visibleItemCount) {
                    coverFlowPagerAdapter.getItem(0)?.date?.let { requestDate ->
                        presenter.getNextIssueMoments(requestDate)
                    }
                }
            }

        }
    }
}