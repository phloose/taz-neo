package de.taz.app.android.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.getViewModel
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.login.fragments.*
import de.taz.app.android.ui.main.*
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

const val ACTIVITY_LOGIN_REQUEST_CODE: Int = 161
const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"
const val LOGIN_EXTRA_REGISTER: String = "LOGIN_EXTRA_REGISTER"
const val LOGIN_EXTRA_ARTICLE = "LOGIN_EXTRA_ARTICLE"

class LoginActivity(
    private val apiService: ApiService = ApiService.getInstance(),
    private val authHelper: AuthHelper = AuthHelper.getInstance(),
    private val articleRepository: ArticleRepository = ArticleRepository.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance(),
    private val toastHelper: ToastHelper = ToastHelper.getInstance()
) : FragmentActivity(R.layout.activity_login) {

    private val log by Log

    private lateinit var viewModel: LoginViewModel

    private var article: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        article = intent.getStringExtra(LOGIN_EXTRA_ARTICLE)?.replace("public.", "")

        navigation_bottom.apply {
            itemIconTintList = null

            // hack to not auto select first item
            menu.getItem(0).isCheckable = false

            setOnNavigationItemSelectedListener {
                this@LoginActivity.apply {
                    val data = Intent()
                    data.putExtra(MAIN_EXTRA_TARGET, MAIN_EXTRA_TARGET_HOME)
                    setResult(Activity.RESULT_CANCELED, data)
                    finish()
                }
                true
            }
        }

        val register = intent.getBooleanExtra(LOGIN_EXTRA_REGISTER, false)
        val username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
        val password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)

        viewModel = getViewModel { LoginViewModel(username, password, register) }

        viewModel.backToArticle = article != null

        viewModel.status.observeDistinct(this) { loginViewModelState: LoginViewModelState? ->
            when (loginViewModelState) {
                LoginViewModelState.INITIAL -> {
                    if (register) {
                        showLoginRequestTestSubscription()
                    } else {
                        showLoginForm()
                    }
                }
                LoginViewModelState.LOADING -> {
                    showLoadingScreen()
                }
                LoginViewModelState.EMAIL_ALREADY_LINKED -> {
                    showEmailAlreadyLinked()
                }
                LoginViewModelState.CREDENTIALS_INVALID -> {
                    showCredentialsInvalid()
                }
                LoginViewModelState.CREDENTIALS_MISSING -> {
                    showMissingCredentials()
                }
                LoginViewModelState.CREDENTIALS_MISSING_INVALID_EMAIL -> {
                    showMissingCredentials(invalidMail = true)
                }
                LoginViewModelState.SUBSCRIPTION_ELAPSED -> {
                    showSubscriptionElapsed()
                }
                LoginViewModelState.SUBSCRIPTION_INVALID -> {
                    showSubscriptionInvalid()
                }
                LoginViewModelState.SUBSCRIPTION_MISSING -> {
                    showSubscriptionMissing()
                }
                LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID -> {
                    showSubscriptionMissing(invalidId = true)
                }
                LoginViewModelState.SUBSCRIPTION_REQUEST -> {
                    showLoginRequestTestSubscription()
                }
                LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL -> {
                    showLoginRequestTestSubscription(invalidMail = true)
                }
                LoginViewModelState.SUBSCRIPTION_TAKEN -> {
                    showSubscriptionTaken()
                }
                LoginViewModelState.PASSWORD_MISSING -> {
                    showLoginForm(passwordErrorId = R.string.login_password_error_empty)
                }
                LoginViewModelState.PASSWORD_REQUEST -> {
                    showPasswordRequest()
                }
                LoginViewModelState.PASSWORD_REQUEST_DONE -> {
                    showPasswordMailSent()
                }
                LoginViewModelState.PASSWORD_REQUEST_NO_MAIL -> {
                    showPasswordRequestNoMail()
                }
                LoginViewModelState.PASSWORD_REQUEST_INVALID_ID -> {
                    showPasswordRequest(invalidId = true)
                }
                LoginViewModelState.POLLING_FAILED -> {
                    toastHelper.showToast(R.string.something_went_wrong_try_later)
                    showLoginForm()
                }
                LoginViewModelState.REGISTRATION_EMAIL -> {
                    showConfirmEmail()
                }
                LoginViewModelState.REGISTRATION_SUCCESSFUL -> {
                    showRegistrationSuccessful()
                }
                LoginViewModelState.USERNAME_MISSING -> {
                    showLoginForm(usernameErrorId = R.string.login_username_error_empty)
                }
                LoginViewModelState.DONE -> {
                    done()
                }
            }
        }

        viewModel.noInternet.observeDistinct(this) {
            if (it) {
                toastHelper.showNoConnectionToast()
            }
        }

    }

    private fun showLoginForm(
        @StringRes usernameErrorId: Int? = null,
        @StringRes passwordErrorId: Int? = null
    ) {
        log.debug("showLoginForm")
        showFragment(
            LoginFragment.create(
                usernameErrorId = usernameErrorId,
                passwordErrorId = passwordErrorId
            )
        )
    }

    private fun hideLoadingScreen() {
        log.debug("hideLoadingScreen")
        loading_screen.visibility = View.GONE
    }

    private fun showLoadingScreen() {
        log.debug("showLoadingScreen")
        loading_screen.visibility = View.VISIBLE
    }

    private fun showConfirmEmail() {
        log.debug("showConfirmEmail")
        showFragment(ConfirmEmailFragment())
    }

    private fun showEmailAlreadyLinked() {
        log.debug("showEmailLinked")
        showFragment(EmailAlreadyLinkedFragment())
    }

    private fun showSubscriptionElapsed() {
        log.debug("showSubscriptionElapsed")
        showFragment(SubscriptionInactiveFragment())
    }

    private fun showSubscriptionMissing(invalidId: Boolean = false) {
        log.debug("showSubscriptionMissing")
        showFragment(SubscriptionMissingFragment.create(invalidId))
    }

    private fun showSubscriptionTaken() {
        log.debug("showSubscriptionTaken")
        showFragment(SubscriptionTakenFragment())
    }

    private fun showMissingCredentials(invalidMail: Boolean = false) {
        log.debug("showMissingCredentials")
        showFragment(CredentialsMissingFragment.create(invalidMail))
    }

    private fun showCredentialsInvalid() {
        log.debug("showCredentialsInvalid")
        showFragment(
            LoginFragment.create(
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showLoginRequestTestSubscription(invalidMail: Boolean = false) {
        log.debug("showLoginRequestTestSubscription")
        showFragment(RequestTestSubscriptionFragment.create(invalidMail))
    }

    private fun showRegistrationSuccessful() {
        log.debug("showLoginRegistrationSuccessful")
        showFragment(RegistrationSuccessfulFragment())
    }

    private fun showPasswordRequest(invalidId: Boolean = false) {
        log.debug("showPasswordRequest")
        showFragment(PasswordRequestFragment.create(invalidId = invalidId))
    }

    private fun showPasswordMailSent() {
        log.debug("showPasswordRequest")
        showFragment(PasswordEmailSentFragment())
    }

    private fun showPasswordRequestNoMail() {
        log.debug("showPasswordRequestNoMail")
        showFragment(PasswordRequestNoMailFragment())
    }

    fun done() {
        log.debug("done")
        showLoadingScreen()

        val data = Intent()
        if (authHelper.isLoggedIn()) {
            runBlocking(Dispatchers.IO) {
                downloadLatestIssueMoments()
            }
            deletePublicIssues()

            article?.let {
                data.putExtra(MAIN_EXTRA_TARGET, MAIN_EXTRA_TARGET_ARTICLE)
                data.putExtra(MAIN_EXTRA_ARTICLE, article)
            } ?: run {
                data.putExtra(MAIN_EXTRA_TARGET, MAIN_EXTRA_TARGET_HOME)
            }
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private suspend fun downloadLatestIssueMoments() {
        val lastIssues = apiService.getLastIssues()
        lastIssues.forEach { issueRepository.save(it) }

        article?.let { article ->
            var lastDate = lastIssues.last().date
            while (articleRepository.get(article) == null) {
                val issues = apiService.getIssuesByDate(lastDate)
                issues.forEach { issueRepository.save(it) }
                lastDate = issues.last().date
            }
        }
    }

    private fun deletePublicIssues() {
        issueRepository.deletePublicIssues()
    }

    private fun showFragment(fragment: Fragment) {
        val fragmentClassName = fragment::class.java.name

        supportFragmentManager.popBackStackImmediate(fragmentClassName, POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.activity_login_fragment_placeholder, fragment)
            addToBackStack(fragmentClassName)
            commit()
        }
        fragment.lifecycleScope.launchWhenResumed {
            hideLoadingScreen()
        }
    }

    override fun onBackPressed() {
        if (loading_screen.visibility == View.VISIBLE) {
            hideLoadingScreen()
        } else {
            if (supportFragmentManager.backStackEntryCount == 1) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                super.onBackPressed()
            }
        }
    }

}