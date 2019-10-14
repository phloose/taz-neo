package de.taz.app.android.ui.webview

import android.os.Bundle
import android.widget.TextView
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class SectionWebViewFragment(val section: Section? = null) : WebViewFragment(), AppWebViewCallback {

    override val menuId: Int = R.menu.navigation_bottom_section
    override val headerId: Int = R.layout.fragment_webview_header_section

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        section?.let {
            CoroutineScope(Dispatchers.IO).launch {
                val issueBase = section.issueBase
                val file = File(
                    ContextCompat.getExternalFilesDirs(
                        requireActivity().applicationContext, null
                    ).first(),
                    "${issueBase.tag}/${section.sectionFileName}"
                )
                lifecycleScope.launch { fileLiveData.value = file }
                activity?.runOnUiThread {
                    view.findViewById<TextView>(R.id.section).apply {
                        text = section.title
                    }
                    view.findViewById<TextView>(R.id.issue_date).apply {
                        val issueDate = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(issueBase.date)
                        text = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMANY).format(issueDate)
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onBottomNavigationItemSelected(menuItem: MenuItem) {

    }
}
