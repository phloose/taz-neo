package de.taz.app.android.ui.settings

import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.preference.*
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.base.BaseContract
import de.taz.app.android.ui.login.LoginFragment
import de.taz.app.android.ui.main.MainContract


class SettingsInnerFragment : PreferenceFragmentCompat(), BaseContract.View  {

    override fun getMainView(): MainContract.View? {
        return activity as? MainContract.View
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return this
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)

        val manageAccountPreference : Preference? = findPreference("manage_account")
        manageAccountPreference?.setOnPreferenceClickListener {
            this.getMainView()?.showMainFragment(LoginFragment())
            true
        }

        findPreference<NightmodePreference>("text_night_mode")?.preferenceManager?.sharedPreferencesName = PREFERENCES_TAZAPICSS
        findPreference<TextSizePreference>("text_font_size")?.preferenceManager?.sharedPreferencesName = PREFERENCES_TAZAPICSS

    }
}