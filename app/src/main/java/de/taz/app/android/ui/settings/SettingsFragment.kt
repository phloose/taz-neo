package de.taz.app.android.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.ui.login.LoginFragment
import de.taz.app.android.ui.splash.CHANNEL_ID_DEBUG
import de.taz.app.android.util.NotificationHelper
import java.util.*

class SettingsFragment : BaseMainFragment<SettingsContract.Presenter>(), SettingsContract.View {

    override val presenter = SettingsPresenter()

    private var storedIssueNumber: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)

        view.apply {

            val tokenText = FirebaseHelper.getInstance().firebaseToken ?: "No push ID"
            findViewById<TextView>(R.id.fragment_settings_push_id).text = tokenText

            findViewById<TextView>(R.id.fragment_settings_header_title).text =
                getString(R.string.settings_header).toLowerCase(
                    Locale.GERMAN
                )

            findViewById<TextView>(R.id.fragment_settings_general_keep_issues).apply {
                setOnClickListener {
                    showKeepIssuesDialog()
                }
            }


            findViewById<Button>(R.id.fragment_settings_account_manage_account)
                .setOnClickListener {
                    this@SettingsFragment.getMainView()?.showMainFragment(LoginFragment())
                }

            findViewById<View>(R.id.settings_text_decrease).setOnClickListener {
                presenter.decreaseTextSize()
            }
            findViewById<View>(R.id.settings_text_increase).setOnClickListener {
                presenter.increaseTextSize()
            }
            findViewById<View>(R.id.settings_text_size).setOnClickListener {
                presenter.resetTextSize()
            }

            findViewById<View>(R.id.fragment_settings_night_mode).apply {
                val switch = findViewById<Switch>(R.id.preference_switch_switch)

                findViewById<TextView>(R.id.preference_switch_title).apply {
                    setText(R.string.settings_text_night_mode)
                    setOnClickListener { switch.toggle() }
                }


                switch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        presenter.enableNightMode()
                    } else {
                        presenter.disableNightMode()
                    }
                }
            }
        }

        presenter.onViewCreated(savedInstanceState)
    }

    private fun showKeepIssuesDialog() {
        context?.let {
            val dialog = AlertDialog.Builder(ContextThemeWrapper(it, R.style.DialogTheme))
                .setView(R.layout.dialog_settings_keep_number)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    val editText = (dialog as AlertDialog).findViewById<EditText>(
                        R.id.dialog_settings_keep_number
                    )
                    editText.text.toString().toIntOrNull()?.let { number ->
                        presenter.setStoredIssueNumber(number)
                        dialog.hide()
                    }
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()

            dialog.show()
            storedIssueNumber?.let {
                dialog?.findViewById<TextView>(
                    R.id.dialog_settings_keep_number
                )?.text = storedIssueNumber
            }
        }
    }

    override fun showStoredIssueNumber(number: String) {
        storedIssueNumber = number
        val text = getString(R.string.settings_general_keep_number_issues, number)
        view?.findViewById<TextView>(R.id.fragment_settings_general_keep_issues)?.text = text
    }

    override fun showNightMode(nightMode: Boolean) {
        view?.findViewById<Switch>(R.id.preference_switch_switch)?.isChecked = nightMode
    }

    override fun showTextSize(textSize: Int) {
        view?.findViewById<TextView>(
            R.id.settings_text_size
        )?.text = getString(R.string.percentage, textSize)
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

}
