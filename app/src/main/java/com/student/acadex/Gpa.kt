package com.student.acadex

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class GpaFragment : Fragment(R.layout.fragment_gpa) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val subjects = repo.subjects()
        val spFilter = view.findViewById<Spinner>(R.id.spSubjectFilter)
        val list = view.findViewById<LinearLayout>(R.id.calcList)
        val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

        // 0 = All, 1 = None (no subject), 2.. = one per subject
        val filterLabels = listOf("All subjects", "No subject") + subjects.map { it.name }
        spFilter.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, filterLabels)

        fun breakdown(g: GradeCalc): String {
            val parts = mutableListOf("Prelim ${g.prelim.trimmed()}", "Midterm ${g.midterm.trimmed()}")
            if (g.structure == "4" && g.semifinal != null) parts.add("Semi-finals ${g.semifinal.trimmed()}")
            parts.add("Finals ${g.finals.trimmed()}")
            return parts.joinToString(" · ")
        }

        fun refresh() {
            val pos = spFilter.selectedItemPosition
            val calcs = when {
                pos == 0 -> repo.gradeCalcs()
                pos == 1 -> repo.gradeCalcs(noSubjectOnly = true)
                else -> repo.gradeCalcs(subjectId = subjects[pos - 2].id)
            }
            list.fill(calcs, R.layout.item_row) { v, g ->
                v.bindRow(
                    g.result.trimmed(),
                    g.subjectName ?: "No subject",
                    breakdown(g),
                    dateFmt.format(Date(g.updatedAt))
                ) { go(R.id.addGpaFragment, id = g.id) }
            }
        }

        spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) = refresh()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        view.findViewById<View>(R.id.btnNewCalc).setOnClickListener { go(R.id.addGpaFragment) }

        refresh()
    }
}

/** Drops a trailing ".0" so whole numbers read cleanly, e.g. "90" instead of "90.0". */
private fun Double.trimmed(): String =
    if (this == Math.floor(this)) toInt().toString() else "%.2f".format(this)

class AddGpaFragment : Fragment(R.layout.fragment_add_gpa) {
    private var result: Double? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val subjects = repo.subjects()

        val spSubject = view.findViewById<Spinner>(R.id.spSubject)
        val rgStructure = view.findViewById<RadioGroup>(R.id.rgStructure)
        val rowSemi = view.findViewById<View>(R.id.rowSemi)

        val etPrelimGrade = view.findViewById<TextInputEditText>(R.id.etPrelimGrade)
        val etPrelimWeight = view.findViewById<TextInputEditText>(R.id.etPrelimWeight)
        val etMidtermGrade = view.findViewById<TextInputEditText>(R.id.etMidtermGrade)
        val etMidtermWeight = view.findViewById<TextInputEditText>(R.id.etMidtermWeight)
        val etSemiGrade = view.findViewById<TextInputEditText>(R.id.etSemiGrade)
        val etSemiWeight = view.findViewById<TextInputEditText>(R.id.etSemiWeight)
        val etFinalsGrade = view.findViewById<TextInputEditText>(R.id.etFinalsGrade)
        val etFinalsWeight = view.findViewById<TextInputEditText>(R.id.etFinalsWeight)

        val tvResult = view.findViewById<TextView>(R.id.tvResult)
        val tvValidation = view.findViewById<TextView>(R.id.tvValidation)
        val btnSave = view.findViewById<View>(R.id.btnSave)
        val delete = view.findViewById<View>(R.id.btnDelete)

        spSubject.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf("No subject") + subjects.map { it.name }
        )

        fun isFourPeriod() = rgStructure.checkedRadioButtonId == R.id.rb4Period

        fun applyDefaultWeights() {
            if (isFourPeriod()) {
                etPrelimWeight.setText("25")
                etMidtermWeight.setText("25")
                etSemiWeight.setText("25")
                etFinalsWeight.setText("25")
            } else {
                etPrelimWeight.setText("33.33")
                etMidtermWeight.setText("33.33")
                etFinalsWeight.setText("33.34")
            }
        }

        fun parse(et: TextInputEditText): Double? = et.text?.toString()?.trim()?.toDoubleOrNull()

        fun recalc() {
            val is4 = isFourPeriod()
            val pg = parse(etPrelimGrade); val pw = parse(etPrelimWeight)
            val mg = parse(etMidtermGrade); val mw = parse(etMidtermWeight)
            val sg = if (is4) parse(etSemiGrade) else null
            val sw = if (is4) parse(etSemiWeight) else null
            val fg = parse(etFinalsGrade); val fw = parse(etFinalsWeight)

            val gradesOk = pg != null && pg >= 0 && mg != null && mg >= 0 && fg != null && fg >= 0 &&
                (!is4 || (sg != null && sg >= 0))
            val weightsOk = pw != null && pw >= 0 && mw != null && mw >= 0 && fw != null && fw >= 0 &&
                (!is4 || (sw != null && sw >= 0))

            if (!gradesOk || !weightsOk) {
                result = null
                tvResult.text = "—"
                tvValidation.text = "Enter all grades and weights"
                tvValidation.visibility = View.VISIBLE
                btnSave.isEnabled = false
                return
            }

            val weightSum = pw!! + mw!! + fw!! + (if (is4) sw!! else 0.0)
            if (abs(weightSum - 100.0) > 0.01) {
                result = null
                tvResult.text = "—"
                tvValidation.text = "Weights must total 100% (currently ${weightSum.trimmed()}%)"
                tvValidation.visibility = View.VISIBLE
                btnSave.isEnabled = false
                return
            }

            val r = pg!! * pw / 100 + mg!! * mw / 100 + fg!! * fw / 100 + (if (is4) sg!! * sw!! / 100 else 0.0)
            result = r
            tvResult.text = r.trimmed()
            tvValidation.visibility = View.GONE
            btnSave.isEnabled = true
        }

        for (et in listOf(etPrelimGrade, etPrelimWeight, etMidtermGrade, etMidtermWeight, etSemiGrade, etSemiWeight, etFinalsGrade, etFinalsWeight)) {
            et.doAfterTextChanged { recalc() }
        }

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { back() }

        val existing = repo.gradeCalc(argId)

        // Re-balance weights to equal defaults when toggling structure on a brand-new
        // calculation; leave an existing calculation's entered weights alone.
        rgStructure.setOnCheckedChangeListener { _, _ ->
            rowSemi.visibility = if (isFourPeriod()) View.VISIBLE else View.GONE
            if (existing == null) applyDefaultWeights()
            recalc()
        }

        if (existing != null) {
            view.findViewById<TextView>(R.id.tvFormTitle).text = "Edit calculation"
            val preset = existing.subjectId
            spSubject.setSelection(subjects.indexOfFirst { it.id == preset } + 1)
            rgStructure.check(if (existing.structure == "4") R.id.rb4Period else R.id.rb3Period)
            rowSemi.visibility = if (existing.structure == "4") View.VISIBLE else View.GONE
            etPrelimGrade.setText(existing.prelim.trimmed())
            etPrelimWeight.setText(existing.prelimWeight.trimmed())
            etMidtermGrade.setText(existing.midterm.trimmed())
            etMidtermWeight.setText(existing.midtermWeight.trimmed())
            if (existing.structure == "4") {
                etSemiGrade.setText(existing.semifinal?.trimmed() ?: "")
                etSemiWeight.setText(existing.semifinalWeight?.trimmed() ?: "")
            }
            etFinalsGrade.setText(existing.finals.trimmed())
            etFinalsWeight.setText(existing.finalsWeight.trimmed())
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                confirm("Delete this calculation?") {
                    repo.deleteGradeCalc(existing.id)
                    back()
                }
            }
        } else {
            val preset = argSubjectId
            spSubject.setSelection(subjects.indexOfFirst { it.id == preset } + 1)
            applyDefaultWeights()
        }
        recalc()

        btnSave.setOnClickListener {
            val r = result
            if (r == null) {
                toast("Fix the grades/weights first")
                return@setOnClickListener
            }
            val is4 = isFourPeriod()
            val pos = spSubject.selectedItemPosition
            val calc = GradeCalc(
                id = existing?.id ?: 0,
                subjectId = if (pos == 0) null else subjects[pos - 1].id,
                structure = if (is4) "4" else "3",
                prelim = parse(etPrelimGrade)!!,
                midterm = parse(etMidtermGrade)!!,
                semifinal = if (is4) parse(etSemiGrade) else null,
                finals = parse(etFinalsGrade)!!,
                prelimWeight = parse(etPrelimWeight)!!,
                midtermWeight = parse(etMidtermWeight)!!,
                semifinalWeight = if (is4) parse(etSemiWeight) else null,
                finalsWeight = parse(etFinalsWeight)!!,
                result = r,
                createdAt = existing?.createdAt ?: 0
            )
            if (repo.saveGradeCalc(calc)) back() else toast("Could not save")
        }
    }
}
