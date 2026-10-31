package com.student.acadex

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment

class DirectoryFragment : Fragment(R.layout.fragment_directory) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ZoomImageView>(R.id.ivCampusMap).setImageResource(R.drawable.acadex_map)

        val placeList = view.findViewById<LinearLayout>(R.id.placeList)
        val collegeList = view.findViewById<LinearLayout>(R.id.collegeList)
        val placesHeader = view.findViewById<View>(R.id.tvPlacesHeader)
        val collegesHeader = view.findViewById<View>(R.id.tvCollegesHeader)
        val noResults = view.findViewById<View>(R.id.tvNoResults)

        // Matches the place name or its location ("Main Building"), and college abbreviation or full name.
        fun show(query: String) {
            val q = query.trim()
            val places = if (q.isEmpty()) CampusDirectory.places
            else CampusDirectory.places.filter { it.name.contains(q, true) || it.kind.contains(q, true) }
            val colleges = if (q.isEmpty()) CampusDirectory.colleges
            else CampusDirectory.colleges.filter { it.abbr.contains(q, true) || it.name.contains(q, true) }

            placesHeader.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
            placeList.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
            collegesHeader.visibility = if (colleges.isEmpty()) View.GONE else View.VISIBLE
            collegeList.visibility = if (colleges.isEmpty()) View.GONE else View.VISIBLE
            noResults.visibility = if (places.isEmpty() && colleges.isEmpty()) View.VISIBLE else View.GONE

            if (places.isNotEmpty()) placeList.fill(places, R.layout.item_row) { v, p ->
                v.bindRow(null, p.name, p.kind) {}
            }
            if (colleges.isNotEmpty()) collegeList.fill(colleges, R.layout.item_row) { v, c ->
                v.bindRow(c.abbr, c.name, null) {}
            }
        }

        show("")
        view.findViewById<EditText>(R.id.etSearch).doAfterTextChanged { show(it?.toString().orEmpty()) }
    }
}
