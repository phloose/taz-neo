package de.taz.app.android.ui.settings

import android.os.Bundle
import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE_DEFAULT
import de.taz.app.android.ui.bottomSheet.textSettings.MAX_TEST_SIZE
import de.taz.app.android.ui.bottomSheet.textSettings.MIN_TEXT_SIZE
import de.taz.app.android.ui.settings.support.ErrorReportFragment
import de.taz.app.android.util.Log
import de.taz.app.android.monkey.observeDistinct
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsPresenter(
    private val authHelper: AuthHelper = AuthHelper.getInstance()
) :
    BasePresenter<SettingsFragment, SettingsDataController>(SettingsDataController::class.java),
    SettingsContract.Presenter {

    private val log by Log

    override fun attach(view: SettingsFragment) {
        super.attach(view)
        view.getMainView()?.getApplicationContext()?.let {
            viewModel?.initializeSettings(it)
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.apply {
            viewModel?.let {
                it.observeTextSize(getLifecycleOwner()) { textSize ->
                    textSize.toIntOrNull()?.let { textSizeInt ->
                        showTextSize(textSizeInt)
                    }
                }
                it.observeNightMode(getLifecycleOwner()) { nightMode ->
                    showNightMode(nightMode)
                }
                it.observeStoredIssueNumber(getLifecycleOwner()) { storedIssueNumber ->
                    showStoredIssueNumber(storedIssueNumber)
                }
            }

            authHelper.authStatusLiveData.observeDistinct(getLifecycleOwner()) { authStatus ->
                if (authStatus == AuthStatus.valid) {
                    showLogoutButton()
                } else {
                    showManageAccountButton()
                }
            }

            authHelper.emailLiveData.observeDistinct(getLifecycleOwner()) { email ->
                fragment_settings_account_email.text = email
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            getView()?.getMainView()?.showHome()
        }
    }

    override fun setStoredIssueNumber(number: Int) {
        log.debug("setKeepNumber: $number")
        viewModel?.setStoredIssueNumber(number)
    }

    override fun disableNightMode() {
        log.debug("disableNightMode")
        viewModel?.setNightMode(false)
    }

    override fun enableNightMode() {
        log.debug("enableNightMode")
        viewModel?.setNightMode(true)
    }

    override fun decreaseTextSize() {
        viewModel?.apply {
            val newSize = getTextSizePercent().toInt() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setTextSizePercent(newSize.toString())
            }
        }
    }

    override fun increaseTextSize() {
        log.debug("increaseTextSize")
        viewModel?.apply {
            val newSize = getTextSizePercent().toInt() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setTextSizePercent(newSize.toString())
            }
        }
    }

    override fun resetTextSize() {
        log.debug("resetTextSize")
        viewModel?.setTextSizePercent(SETTINGS_TEXT_FONT_SIZE_DEFAULT)
    }

    override fun reportBug() {
        getView()?.getMainView()?.showMainFragment(ErrorReportFragment())
    }

    override fun logout() {
        authHelper.token = ""
        authHelper.authStatus = AuthStatus.notValid
    }
}