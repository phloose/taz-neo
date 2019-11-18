package de.taz.app.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.login.LoginFragment
import kotlinx.android.synthetic.main.fragment_settings.*


class SettingsFragment : BaseMainFragment<SettingsPresenter>() {
    override val presenter: SettingsPresenter = SettingsPresenter()

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
        presenter.onViewCreated(savedInstanceState)

        fragment_settings_manage_account_button.setOnClickListener {
            presenter.showLoginFragment()
        }
    }
}