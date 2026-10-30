package com.student.acadex

data class Place(val name: String, val kind: String)

/**
 * Entries shown in the Campus Directory / Map page list.
 */
object CampusDirectory {
    val places = listOf(
        Place("Library", "Main Building, 3rd Floor"),
        Place("Registrar's Office", "Main Building, 2nd Floor"),
        Place("Annex C", "Top Side of the Map"),
        Place("Annex D", "Right Side of the Main Building"),
        Place("Annex", "Left Side, In Front of the Main Building"),
        Place("College of Information Technology (CIT) Office", "Bottom-Left Side of the Map"),
        Place("Digitel", "Right Side of the Map"),
        Place("Hospitality Management (HM) Office", "Right Side of the Map, Near Digitel"),
        Place("Office of Student Affairs (OSA)", "Main Building, 2nd Floor"),
        Place("Clinic", "Top-Right Side of the Map"),
        Place("Civil Engineering Building", "Top-Right Side of the Map"),
        Place("Criminology Building", "Bottom-Right Side of the Map"),
        Place("Guidance Office", "In Front of the Building Beside the CIT Office")
    )
}
