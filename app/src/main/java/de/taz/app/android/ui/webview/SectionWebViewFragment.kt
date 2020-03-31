package de.taz.app.android.ui.webview

import android.graphics.Typeface
import android.view.MenuItem
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SectionWebViewFragment : WebViewFragment<SectionStub>(R.layout.fragment_webview_section) {

    private val dateHelper: DateHelper = DateHelper.getInstance()
    override val viewModel = WebViewViewModel<SectionStub>()
    override val nestedScrollViewId: Int = R.id.web_view_wrapper

    companion object {
        fun createInstance(section: SectionStub): SectionWebViewFragment {
            val fragment = SectionWebViewFragment()
            fragment.displayable = section
            return fragment
        }
    }

    override fun setHeader(displayable: SectionStub) {
        activity?.apply {

            lifecycleScope.launch(Dispatchers.IO) {
                val issueOperations = displayable.getIssueOperations()
                issueOperations.apply {
                    if(isWeekend) {
                        FileHelper.getInstance().getFile(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)?.let {
                            val typeface = Typeface.createFromFile(it)
                            runOnUiThread {
                                view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                            }
                        }
                    }
                }
            }

            runOnUiThread {
                view?.findViewById<TextView>(R.id.section)?.apply {
                    text = displayable.getHeaderTitle()
                }
                dateHelper.dateToLowerCaseString(displayable.issueDate)?.let {
                    view?.findViewById<TextView>(R.id.issue_date)?.apply {
                        text = it
                    }
                }
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome()
            }
            R.id.bottom_navigation_action_size -> {
                showFontSettingBottomSheet()
            }
        }
    }
}