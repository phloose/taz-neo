package de.taz.app.android.ui.webview

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_section.web_view
import kotlinx.android.synthetic.main.fragment_webview_section.web_view_spinner


abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable> :
    BaseMainFragment<WebViewPresenter<DISPLAYABLE>>(), WebViewContract.View<DISPLAYABLE>,
    BackFragment {

    private val log by Log

    private lateinit var tazApiCssPreferences : SharedPreferences

    override val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(p0: View, p1: Float) {
        }

        override fun onStateChanged(bottomSheetView: View, state: Int) {
            if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.apply {
                    this@WebViewFragment.deactivateAllItems(menu)
                }
            }
        }
    }

    private val tazApiCssPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        log.debug("WebViewFragment: shared pref changed: $key")
        presenter.injectCss(sharedPreferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

       getMainView()?.getApplicationContext()?.let {applicationContext ->
           tazApiCssPreferences = applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
           tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
       }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

    }

    override fun getWebView(): AppWebView {
        return web_view
    }

    override fun hideLoadingScreen() {
        activity?.runOnUiThread {
            web_view_spinner?.visibility = View.GONE
            web_view?.visibility = View.VISIBLE
        }
    }

    override fun loadUrl(url: String) {
        activity?.runOnUiThread {
            view?.findViewById<WebView>(R.id.web_view)?.loadUrl(url)
        }
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

    override fun onBackPressed(): Boolean {
        return presenter.onBackPressed()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        presenter.onBottomNavigationItemClicked(menuItem, activated)
    }

    override fun shareText(text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    override fun showBookmarkBottomSheet() {
        val article = getWebViewDisplayable() as? Article
        article?. let {
            showBottomSheet(BookmarkSheetFragment(article.articleFileName))
        }
    }

    override fun showFontSettingBottomSheet() {
        showBottomSheet(TextSettingsFragment())
    }

    override fun onDestroy() {
        super.onDestroy()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }
}
