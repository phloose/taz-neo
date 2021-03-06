package de.taz.app.android.ui.home.page

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.Log

/**
 * [HomePageOnScrollListener] ensures endless scrolling is possible
 * if there aren't enough next items they will be downloaded
 */
open class HomePageOnScrollListener(
    private val homePageFragment: HomePageFragment
) : RecyclerView.OnScrollListener() {

    private val log by Log

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        val visibleItemCount = lastVisibleItem - firstVisibleItem
        val totalItemCount = layoutManager.itemCount

        if (lastVisibleItem > totalItemCount - 2 * visibleItemCount) {
            homePageFragment.adapter?.getItem(totalItemCount - 1)?.date?.let { date ->
                log.debug("requesting next issues")
                homePageFragment.getNextIssueMoments(date)
            }
        }
    }

}