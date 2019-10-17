package de.taz.app.android.ui.drawer.bookmarks

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.Issue

class BookmarksViewModel : ViewModel() {

    val bookmarkedArticleBases = MutableLiveData<List<ArticleBase>?>().apply {
        postValue(null)
    }
}
