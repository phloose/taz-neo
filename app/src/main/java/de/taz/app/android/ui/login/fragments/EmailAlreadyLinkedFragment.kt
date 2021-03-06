package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_email_already_taken.*

class EmailAlreadyLinkedFragment : LoginBaseFragment(R.layout.fragment_login_email_already_taken) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_email_already_taken_insert_new.setOnClickListener {
            viewModel.apply {
                username = ""
                password = ""
                viewModel.status.postValue(viewModel.statusBeforeEmailAlreadyLinked)
                viewModel.statusBeforeEmailAlreadyLinked = null
            }
        }
        fragment_login_email_already_taken_contact_email.setOnClickListener {
            writeEmail()
        }
    }

}