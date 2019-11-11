package de.taz.app.android.ui.webview

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val PREFERENCES_TAZAPI = "preferences_tazapi"

class TazApiJS (val view: WebViewContract.View) {

    val context = view.getMainView()?.getApplicationContext()

    private val log by Log

    @JavascriptInterface
    fun getConfiguration(name: String) : String {
        log.debug("getConfiguration $name")
        val sharedPreferences = context?.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        return sharedPreferences?.getString(name, "") ?: ""
    }

    @JavascriptInterface
    fun setConfiguration(name: String, value: String) {
        log.debug("setConfiguration $name: $value")
        val sharedPref = context?.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        sharedPref?.apply{
            with (sharedPref.edit()) {
                putString(name, value)
                commit()
            }
        }
    }

    @JavascriptInterface
    fun pageReady(percentage: Int, position: Int) {
        log.debug("pageReady $percentage $position")
        view.getWebViewDisplayable()?.let {
            if (it is Article) {
                ArticleRepository.getInstance().saveScrollingPosition(it, percentage, position)
            }
        }
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.debug("nextArticle $position")
        view.getMainView()?.let { mainView ->
            mainView.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                view.getWebViewDisplayable()?.next()?.let { next ->
                    mainView.showInWebView(next, R.anim.slide_in_left, R.anim.slide_out_left)
                }
            }
        }
    }

    @JavascriptInterface
    fun previousArticle(position: Int = 0) {
        log.debug("previousArticle $position")
        view.getMainView()?.let { mainView ->
            mainView.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                view.getWebViewDisplayable()?.previous()?.let { previous ->
                    mainView.showInWebView(previous, R.anim.slide_in_right, R.anim.slide_out_right)
                }
            }
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        log.debug("openUrl $url")
        // relevant for links in the title for instance

        //TODO urls in title should be adapted to logged in/not logged in users
        // we should just use "url" here to get the article
        // right now the links only work for logged in users
        // these lines are needed to bend it for not logged in users
        //val (name, suffix) = url.split(".")
        //val newUrl = "$name.public.$suffix"

        view.getMainView()?.let {
            it.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                val article = ArticleRepository.getInstance().get(url)
                val articleWebViewFragment = ArticleWebViewFragment(article)
                it.showInWebView(articleWebViewFragment.getWebViewDisplayable() as Article)
            }
        }
    }
}
