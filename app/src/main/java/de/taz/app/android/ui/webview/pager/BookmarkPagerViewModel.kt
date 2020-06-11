package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkPagerViewModel(application: Application) : AndroidViewModel(application) {

    val articleNameLiveData = MutableLiveData<String?>(null)
    val articleName: String?
        get() = articleNameLiveData.value

    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value ?: 0

    val articleList
        get() = articleListLiveData.value ?: emptyList()
    val articleListLiveData = MediatorLiveData<List<ArticleStub>>().apply {
        addSource(articleNameLiveData) {
            it?.let {
                getBookmarkedArticles()
            }
        }
    }

    var sectionNameListLiveData = MutableLiveData<List<String?>>(emptyList())

    var issueStubListLiveData = MutableLiveData<List<IssueOperations?>>(emptyList())

    private fun getBookmarkedArticles() {
        articleListLiveData.apply {
            CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                val bookmarkedArticles =
                    ArticleRepository.getInstance(getApplication()).getBookmarkedArticleStubList()
                postValue(bookmarkedArticles)
                sectionNameListLiveData.postValue(bookmarkedArticles.map { it.getSectionStub()?.key })
                issueStubListLiveData.postValue(bookmarkedArticles.map { it.getIssueOperations() })
                if (currentPosition <= 0) {
                    currentPositionLiveData.postValue(
                        bookmarkedArticles.indexOfFirst { it.key == articleName }
                    )
                }
            }
        }
    }

}