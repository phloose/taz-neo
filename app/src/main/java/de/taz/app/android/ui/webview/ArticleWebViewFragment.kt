package de.taz.app.android.ui.webview

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.ui.BackFragment
import kotlinx.android.synthetic.main.fragment_webview.*
import kotlinx.coroutines.*
import java.io.File

class ArticleWebViewFragment(val article: Article? = null) : WebViewFragment(), AppWebViewCallback, BackFragment {

    override val menuId: Int = R.menu.navigation_bottom_article
    override val headerId: Int = R.layout.fragment_webview_header_article

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        web_view_wrapper.updatePadding(20, 0, 20, 0)
       super.onViewCreated(view, savedInstanceState)
    }

    override fun onAttach(context: Context) {
        activity?.let {activity ->
            article?.let {article ->
                lifecycleScope.launch(Dispatchers.IO) {
                    article.getSection()?.let { section ->
                        article.getIndexInSection()?.let {articleIndex ->
                            val file = File(
                                ContextCompat.getExternalFilesDirs(
                                    context.applicationContext, null
                                ).first(),
                                "${section.issueBase.tag}/${article.articleFileName}"
                            )
                            lifecycleScope.launch { fileLiveData.value = file }
                            activity.runOnUiThread {
                                activity.findViewById<TextView>(R.id.section).apply {
                                    text = section.title
                                }
                                activity.findViewById<TextView>(R.id.article_num).apply {
                                    text = activity
                                        .getString(R.string.fragment_header_article,
                                            articleIndex,
                                            section.articleList.size)
                                }
                            }
                        }
                    }
                }
            }
        }

        super.onAttach(context)
    }

    override fun onBottomNavigationItemSelected(menuItem: MenuItem) {
        when(menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> {
                article?.let {
                    activity?.let { activity ->
                        if (article.bookmarked) {
                            val bookmark = navigation_bottom.menu.findItem(
                                R.id.bottom_navigation_action_bookmark
                            )
                            bookmark.icon = activity.getDrawable(R.drawable.ic_bookmark_red)
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return runBlocking {
            withContext(Dispatchers.IO) {
                article?.getSection()?.let { section ->
                    (activity as MainActivity).let { it.runOnUiThread { it.showSection(section) }}
                    return@withContext true
                }
                return@withContext false
            }
        }
    }

    override fun onSwipeLeft(e1: MotionEvent, e2: MotionEvent) {
        super.onSwipeLeft(e1, e2)
        lifecycleScope.launch(Dispatchers.IO) {
            article?.nextArticle()?.let {
                (activity as MainActivity).showArticle(it, R.anim.slide_in_left, R.anim.slide_out_left)
            }
        }
    }

    override fun onSwipeRight(e1: MotionEvent, e2: MotionEvent) {
        super.onSwipeLeft(e1, e2)
        lifecycleScope.launch(Dispatchers.IO) {
            article?.previousArticle()?.let {
                (activity as MainActivity).showArticle(it, R.anim.slide_in_right, R.anim.slide_out_right)
            }
        }
    }

}

