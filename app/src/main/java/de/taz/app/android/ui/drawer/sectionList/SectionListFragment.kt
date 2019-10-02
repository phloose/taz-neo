package de.taz.app.android.ui.drawer.sectionList

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_drawer_menu_list.*

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionListFragment : Fragment() {

    private lateinit var viewModel: SelectedIssueViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drawer_menu_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity()).get(SelectedIssueViewModel::class.java)

        val recycleAdapter =
            SectionListAdapter(requireActivity() as MainActivity)
        drawer_menu_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionListFragment.context)
            adapter = recycleAdapter
        }
        viewModel.selectedIssue.observe(this, Observer { issue ->
            recycleAdapter.setData(issue)
            activity?.let {
                it.findViewById<TextView>(R.id.drawerDateText).text = issue?.date ?: ""
            }
// TODO            activity?.findViewById<ImageView>(R.id.drawerMoment)
        })
    }

}
