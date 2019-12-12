package de.taz.app.android.ui.main

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.annotation.AnimRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import de.taz.app.android.BuildConfig
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.download.RESOURCE_FOLDER
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerContract
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.TazApiCssHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity(), MainContract.View {

    private val log by Log

    private val fileHelper = FileHelper.getInstance()

    private val tazApiCssHelper = TazApiCssHelper.getInstance()

    private val presenter = MainPresenter()

    private lateinit var tazApiCssPreferences : SharedPreferences

    private val tazApiCssPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        log.debug("Shared pref changed: $key")
        val cssFile = fileHelper.getFile("$RESOURCE_FOLDER/tazApi.css")
        val cssString = tazApiCssHelper.generateCssString(sharedPreferences)

        cssFile.writeText(cssString)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter.attach(this)

        setContentView(R.layout.activity_main)
        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)

            activity_main_version.text = BuildConfig.VERSION_NAME
            activity_main_version.visibility = View.VISIBLE
        }

        presenter.onViewCreated(savedInstanceState)

        lockEndNavigationView()

        tazApiCssPreferences = applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
        PreferenceManager.setDefaultValues(this, PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE, R.xml.fragment_settings, false)
        tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)

    }

    override fun onResume() {
        super.onResume()
        tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    override fun onPause() {
        super.onPause()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    override fun getMainDataController(): MainContract.DataController {
        return presenter.viewModel as MainContract.DataController
    }

    override fun showDrawerFragment(fragment: Fragment) {
        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.drawer_menu_fragment_placeholder,
                    fragment
                )
                .commit()
        }
    }

    override fun showInWebView(
        webViewDisplayable: WebViewDisplayable,
        enterAnimation: Int,
        exitAnimation: Int
    ) {
        when (webViewDisplayable) {
            is Article -> showArticle(webViewDisplayable, enterAnimation, exitAnimation)
            is Section -> showSection(webViewDisplayable, enterAnimation, exitAnimation)
        }
    }

    private fun showArticle(article: Article, enterAnimation: Int, exitAnimation: Int) {
        val fragment = ArticlePagerFragment.createInstance(article)
        showMainFragment(fragment, enterAnimation, exitAnimation)
    }

    private fun showSection(section: Section, enterAnimation: Int, exitAnimation: Int) {
        runOnUiThread {
            if (!tryShowExistingSection(section)) {
                val fragment = SectionPagerFragment.createInstance(section)
                showMainFragment(fragment, enterAnimation, exitAnimation)
            }
        }
    }

    @MainThread
    private fun tryShowExistingSection(section: Section): Boolean {
        val currentFragment =
            supportFragmentManager.findFragmentById(R.id.main_content_fragment_placeholder)
        if (currentFragment is SectionPagerContract.View) {
            return currentFragment.tryLoadSection(section)
        }
        return false
    }

    override fun showMainFragment(fragment: Fragment, @AnimRes enterAnimation: Int, @AnimRes exitAnimation: Int) {
        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(enterAnimation, exitAnimation)
                .replace(
                    R.id.main_content_fragment_placeholder, fragment
                )
                .addToBackStack(fragment::javaClass.name)
                .commit()
        }
    }

    override fun lockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    override fun unlockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
    }

    override fun closeDrawer() {
        drawer_layout.closeDrawers()
    }

    /**
     * Workaround for AppCompat 1.1.0 and WebView on API 21 - 25
     * See: https://issuetracker.google.com/issues/141132133
     * TODO: try to remove when updating appcompat
     */
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (Build.VERSION.SDK_INT in 21..25 && (resources.configuration.uiMode == applicationContext.resources.configuration.uiMode)) {
            return
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    /**
     * if currently shown fragment implements onBackPressed and returns true it has handled the
     * back button
     */
    override fun onBackPressed() {
        val count = supportFragmentManager.backStackEntryCount

        // TODO: check if BottomSheet is shown, then hide it before popStack
        if (count > 1) {
            supportFragmentManager.findFragmentById(R.id.main_content_fragment_placeholder)?.let {
                if (it is BackFragment && it.onBackPressed()) {
                    return
                } else {
                    supportFragmentManager.popBackStack()
                }
            }
        }
    }

    override fun showHome() {
        supportFragmentManager.popBackStack(
                supportFragmentManager.getBackStackEntryAt(1).id,
                1
            )
    }

    override fun showToast(stringId: Int) {
        ToastHelper.getInstance(applicationContext).makeToast(stringId)
    }

    override fun showToast(string: String) {
        ToastHelper.getInstance(applicationContext).makeToast(string)
    }

    override fun getLifecycleOwner(): LifecycleOwner = this

    override fun getMainView(): MainContract.View? = this

    override fun setDrawerIssue(issueOperations: IssueOperations?) {
        lifecycleScope.launch(Dispatchers.IO) {
            presenter.viewModel?.setIssueOperations(issueOperations)
        }
    }

    override fun hideKeyboard() {
        val inputMethodManager: InputMethodManager = getSystemService(
            Activity.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        val view = currentFocus ?: View(this)
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
