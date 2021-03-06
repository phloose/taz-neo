package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_subscription_address.*

const val MAX_NAME_LENGTH = 24

class SubscriptionAddressFragment :
    SubscriptionBaseFragment(R.layout.fragment_subscription_address) {
    var cityInvalid: Boolean = false
    var countryInvalid: Boolean = false
    var postcodeInvalid: Boolean = false
    var streetInvalid: Boolean = false
    var nameTooLong: Boolean = false
    var firstNameEmpty: Boolean = false
    var firstNameInvalid: Boolean = false
    var surnameEmpty: Boolean = false
    var surnameInvalid: Boolean = false

    companion object {
        fun createInstance(
            cityInvalid: Boolean = false,
            countryInvalid: Boolean = false,
            postcodeInvalid: Boolean = false,
            streetInvalid: Boolean = false,
            nameTooLong: Boolean = false,
            firstNameEmpty: Boolean = false,
            firstNameInvalid: Boolean = false,
            surnameEmpty: Boolean = false,
            surnameInvalid: Boolean = false
        ): SubscriptionAddressFragment {
            val fragment = SubscriptionAddressFragment()
            fragment.cityInvalid = cityInvalid
            fragment.countryInvalid = countryInvalid
            fragment.postcodeInvalid = postcodeInvalid
            fragment.streetInvalid = streetInvalid
            fragment.nameTooLong = nameTooLong
            fragment.firstNameEmpty = firstNameEmpty
            fragment.firstNameInvalid = firstNameInvalid
            fragment.surnameEmpty = surnameEmpty
            fragment.surnameInvalid = surnameInvalid
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_subscription_address_first_name_layout.markRequired()
        fragment_subscription_address_surname_layout.markRequired()
        fragment_subscription_address_street_layout.markRequired()
        fragment_subscription_address_city_layout.markRequired()
        fragment_subscription_address_country_layout.markRequired()
        fragment_subscription_address_postcode_layout.markRequired()

        if (viewModel.price == 0) {
            fragment_subscription_address_street_layout.visibility = View.GONE
            fragment_subscription_address_city_layout.visibility = View.GONE
            fragment_subscription_address_postcode_layout.visibility = View.GONE
            fragment_subscription_address_country_layout.visibility = View.GONE
            fragment_subscription_address_phone_layout.visibility = View.GONE
            fragment_subscription_address_name_affix.imeOptions = EditorInfo.IME_ACTION_DONE
        }

        fragment_subscription_address_first_name.setText(viewModel.firstName)
        fragment_subscription_address_surname.setText(viewModel.surName)
        fragment_subscription_address_street.setText(viewModel.street)
        fragment_subscription_address_city.setText(viewModel.city)
        fragment_subscription_address_country.setText(viewModel.country)
        fragment_subscription_address_postcode.setText(viewModel.postCode)

        fragment_subscription_address_first_name.doAfterTextChanged { text ->
            fragment_subscription_address_surname_layout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        fragment_subscription_address_surname.doAfterTextChanged { text ->
            fragment_subscription_address_first_name_layout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        fragment_subscription_address_phone.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        fragment_subscription_address_name_affix.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        fragment_subscription_address_proceed.setOnClickListener {
            ifDoneNext()
        }

        if (nameTooLong) {
            setFirstNameError(R.string.login_first_name_helper)
            setSurnameError(R.string.login_surname_helper)
        }

        if (firstNameEmpty) {
            setFirstNameError(R.string.login_first_name_error_empty)
        }
        if (firstNameInvalid) {
            setFirstNameError(R.string.login_first_name_error_invalid)
        }
        if (surnameEmpty) {
            setSurnameError(R.string.login_surname_error_empty)
        }
        if (surnameInvalid) {
            setSurnameError(R.string.login_surname_error_invalid)
        }

        if (cityInvalid) {
            fragment_subscription_address_city_layout.setError(R.string.subscription_field_invalid)
        }
        if (countryInvalid) {
            fragment_subscription_address_country_layout.setError(R.string.subscription_field_invalid)
        }
        if (streetInvalid) {
            fragment_subscription_address_street_layout.setError(R.string.subscription_field_invalid)
        }
        if (postcodeInvalid) {
            fragment_subscription_address_postcode_layout.setError(R.string.subscription_field_invalid)
        }
    }

    override fun done(): Boolean {
        var done = true
        if (fragment_subscription_address_first_name.text.isNullOrBlank()) {
            setFirstNameError(R.string.login_first_name_error_empty)
            done = false
        }
        if (fragment_subscription_address_surname.text.isNullOrBlank()) {
            setSurnameError(R.string.login_surname_error_empty)
            done = false
        }
        if (fragment_subscription_address_street_layout.isVisible
            && fragment_subscription_address_street.text.isNullOrBlank()
        ) {
            fragment_subscription_address_street_layout.error =
                context?.getString(R.string.street_error_empty)
            done = false
        }
        if (fragment_subscription_address_city_layout.isVisible
            && fragment_subscription_address_city.text.isNullOrBlank()
        ) {
            fragment_subscription_address_city_layout.error =
                context?.getString(R.string.city_error_empty)
            done = false
        }
        if (fragment_subscription_address_country_layout.isVisible
            && fragment_subscription_address_country.text.isNullOrBlank()
        ) {
            fragment_subscription_address_country_layout.error =
                context?.getString(R.string.country_error_empty)
            done = false
        }
        if (fragment_subscription_address_postcode_layout.isVisible
            && fragment_subscription_address_postcode.text.isNullOrBlank()
        ) {
            fragment_subscription_address_postcode_layout.error =
                context?.getString(R.string.postcode_error_empty)
            done = false
        }
        viewModel.apply {
            firstName = fragment_subscription_address_first_name.text.toString()
            surName = fragment_subscription_address_surname.text.toString()
            street = fragment_subscription_address_street.text.toString()
            city = fragment_subscription_address_city.text.toString()
            country = fragment_subscription_address_country.text.toString()
            postCode = fragment_subscription_address_postcode.text.toString()
            phone = fragment_subscription_address_phone.text.toString()
        }
        return done
    }

    override fun next() {
        if (viewModel.price == 0) {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT)
        } else {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_BANK)

        }
    }

    fun setFirstNameError(@StringRes stringRes: Int) {
        fragment_subscription_address_first_name_layout.setError(stringRes)
    }

    fun setSurnameError(@StringRes stringRes: Int) {
        fragment_subscription_address_surname_layout.setError(stringRes)
    }
}