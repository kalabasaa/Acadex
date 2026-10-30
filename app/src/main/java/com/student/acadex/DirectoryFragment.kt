package com.student.acadex

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class DirectoryFragment : Fragment(R.layout.fragment_directory) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val map = view.findViewById<ZoomImageView>(R.id.ivCampusMap)
        val mapRes = resources.getIdentifier("campus_map", "drawable", requireContext().packageName)
        map.setImageResource(if (mapRes != 0) mapRes else R.drawable.campus_map_placeholder)

        val placeList = view.findViewById<LinearLayout>(R.id.placeList)
        placeList.fill(CampusDirectory.places, R.layout.item_row) { v, p ->
            v.bindRow(null, p.name, p.kind) {}
        }
    }
}
