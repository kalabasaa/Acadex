package com.student.acadex

data class Place(val name: String, val kind: String)

data class College(val abbr: String, val name: String)

/**
 * Entries shown in the Campus Directory / Map page list.
 */
object CampusDirectory {
    val places = listOf(
        Place("Library", "Main Building, 3rd Floor"),
        Place("Registrar's Office", "Main Building, 2nd Floor"),
        Place("Annex C", "Right Side of the Main Building"),
        Place("Annex D", "Top Side of the Map"),
        Place("Annex", "Left Side, In Front of the Main Building"),
        Place("CIT Office", "Bottom-Left Side of the Map"),
        Place("Digitel", "Right Side of the Map"),
        Place("CHM Office", "Right Side of the Map, Near Digitel"),
        Place("OSA Office", "Main Building, 2nd Floor"),
        Place("Clinic", "Top-Right Side of the Map"),
        Place("Civil Engineering Building", "Top-Right Side of the Map"),
        Place("Criminology Building", "Bottom-Right Side of the Map"),
        Place("Guidance Office", "In Front of the Building Beside the CIT Office"),
        Place("CE Office", "Civil Engineering Building, 1st Floor"),
        Place("CTE Office", "Main Building, 2nd Floor"),
        Place("CCJE Office", "Main Building, 2nd Floor"),
        Place("ComLab", "Main Building, 2nd Floor"),
        Place("CAAIS Office", "Bottom-Left Side of the Map"),
        Place("CM Office", "Main Building, 2nd Floor")
    )

    val colleges = listOf(
        College("CHM", "College of Hospitality Management"),
        College("CBA", "College of Business Administration"),
        College("CCJE", "College of Criminal Justice Education"),
        College("CE", "College of Engineering"),
        College("CAAIS", "College of Accountancy & Accounting Information System"),
        College("CTE", "College of Teacher Education"),
        College("CIT", "College of Information Technology"),
        College("CM", "College of Midwifery")
    )
}
