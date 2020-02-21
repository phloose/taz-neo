package de.taz.app.android.ui.home.feedFilter

import android.os.Bundle
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.singletons.FeedHelper

class FeedFilterPresenter(
    private val feedHelper: FeedHelper = FeedHelper.getInstance()
) :
    BasePresenter<FeedFilterContract.View, FeedFilterDataController>(
        FeedFilterDataController::class.java
    ), FeedFilterContract.Presenter {


    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.apply {
                    observeFeeds(it) { feeds ->
                        view.setFeeds(feeds ?: emptyList())
                    }
                    observeInactiveFeedNames(it) { inactiveFeedNames ->
                        view.setInactiveFeedNames(inactiveFeedNames)
                    }
                }
            }
        }
    }

    override fun onFeedClicked(feed: Feed) {
        val inactiveFeedNames = viewModel?.getInactiveFeedNames() ?: emptySet()
        if (feed.name !in inactiveFeedNames) {
            feedHelper.deactivateFeed(feed)
        } else {
            feedHelper.activateFeed(feed)
        }
    }

}