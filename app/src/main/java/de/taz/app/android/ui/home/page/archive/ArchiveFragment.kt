package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.home.HomePresenter
import de.taz.app.android.ui.home.page.HomePageListAdapter
import kotlinx.android.synthetic.main.fragment_archive.*

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : BaseMainFragment<ArchiveContract.Presenter>(),
    ArchiveContract.View {

    override val presenter = ArchivePresenter()
    val archiveListAdapter =
        HomePageListAdapter(
            this,
            R.layout.fragment_archive_item,
            presenter
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns(context))
        }
        fragment_archive_grid.adapter = archiveListAdapter

        presenter.onViewCreated(savedInstanceState)

        fragment_archive_grid.addOnScrollListener(
            ArchiveOnScrollListener(
                this
            )
        )

    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        (fragment_archive_grid?.adapter as? HomePageListAdapter)?.setIssueStubs(issueStubs)
    }

    private fun calculateNoOfColumns(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        val columnWidthDp =
            resources.getDimension(R.dimen.fragment_archive_item_width) / displayMetrics.density
        return (screenWidthDp / columnWidthDp).toInt()
    }

    override fun setFeeds(feeds: List<Feed>) {
       archiveListAdapter.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        archiveListAdapter.setInactiveFeedNames(inactiveFeedNames)
    }

}