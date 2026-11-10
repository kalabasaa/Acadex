package com.student.acadex

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class SharedClass(
    val day: String,
    val start: String,
    val end: String,
    val type: String,
    val room: String?,
    val online: String?
)

data class SharedSubject(
    val name: String,
    val code: String,
    val professor: String?,
    val classes: List<SharedClass>
)

private val VALID_DAYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

fun buildSharePayload(subjects: List<SharedSubject>): String {
    val root = JSONObject()
    root.put("app", "acadex")
    root.put("type", "share")
    root.put("v", 1)
    val arr = JSONArray()
    subjects.forEach { s ->
        val so = JSONObject()
        so.put("name", s.name)
        so.put("code", s.code)
        so.put("professor", s.professor ?: JSONObject.NULL)
        val classesArr = JSONArray()
        s.classes.forEach { c ->
            val co = JSONObject()
            co.put("day", c.day)
            co.put("start", c.start)
            co.put("end", c.end)
            co.put("type", c.type)
            co.put("room", c.room ?: JSONObject.NULL)
            co.put("online", c.online ?: JSONObject.NULL)
            classesArr.put(co)
        }
        so.put("classes", classesArr)
        arr.put(so)
    }
    root.put("subjects", arr)
    return root.toString()
}

/** Parses and validates a scanned payload. First is an error message (null if valid); second is the parsed subjects. */
fun parseSharePayload(json: String): Pair<String?, List<SharedSubject>> {
    try {
        val root = JSONObject(json)
        if (root.optString("app") != "acadex" || root.optString("type") != "share") {
            return "This QR code isn't an Acadex share code." to emptyList()
        }
        val arr = root.optJSONArray("subjects")
        if (arr == null || arr.length() == 0) {
            return "This QR code has no subjects in it." to emptyList()
        }
        val subjects = ArrayList<SharedSubject>()
        for (i in 0 until arr.length()) {
            val so = arr.getJSONObject(i)
            val name = so.optString("name").trim()
            val code = so.optString("code").trim()
            if (name.isEmpty() || code.isEmpty()) {
                return "Subject #${i + 1} is missing a name or code." to emptyList()
            }
            val professor = so.optString("professor", "").trim().ifEmpty { null }
            val classesArr = so.optJSONArray("classes") ?: JSONArray()
            val classes = ArrayList<SharedClass>()
            for (j in 0 until classesArr.length()) {
                val co = classesArr.getJSONObject(j)
                val day = co.optString("day").trim().uppercase()
                val start = co.optString("start").trim()
                val end = co.optString("end").trim()
                val type = co.optString("type").trim()
                val room = co.optString("room", "").trim().ifEmpty { null }
                val online = co.optString("online", "").trim().ifEmpty { null }

                if (day !in VALID_DAYS) return "\"$name\": one of its classes has an invalid day." to emptyList()
                if (!TIME_RE.matches(start) || !TIME_RE.matches(end) || start >= end) {
                    return "\"$name\": one of its classes has an invalid time range." to emptyList()
                }
                if (type != "Online" && type != "Onsite") {
                    return "\"$name\": one of its classes has an invalid class type." to emptyList()
                }
                if (type == "Online" && online.isNullOrEmpty()) {
                    return "\"$name\": an online class is missing its online details." to emptyList()
                }
                if (type == "Onsite" && room.isNullOrEmpty()) {
                    return "\"$name\": an onsite class is missing its room." to emptyList()
                }
                classes.add(SharedClass(day, start, end, type, if (type == "Onsite") room else null, if (type == "Online") online else null))
            }
            subjects.add(SharedSubject(name, code, professor, classes))
        }
        return null to subjects
    } catch (e: Exception) {
        return "This isn't a valid Acadex QR code." to emptyList()
    }
}

class ShareQrFragment : Fragment(R.layout.fragment_share_qr) {
    private var lastQrBitmap: Bitmap? = null
    private var lastScanned: List<SharedSubject>? = null
    private val previewChecks = LinkedHashMap<Pair<Int, Int>, CheckBox>()

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(QrScanActivity.EXTRA_TEXT)?.let { onScanResult(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshSubjectChecklist()

        view.findViewById<View>(R.id.btnGenerate).setOnClickListener { generateQr() }
        view.findViewById<View>(R.id.btnShareImage).setOnClickListener { shareQrImage() }
        view.findViewById<View>(R.id.btnScan).setOnClickListener {
            scanLauncher.launch(Intent(requireContext(), QrScanActivity::class.java))
        }
        view.findViewById<View>(R.id.btnImportCancel).setOnClickListener { clearImportPreview() }
        view.findViewById<View>(R.id.btnImportConfirm).setOnClickListener { confirmImport() }
    }

    private fun refreshSubjectChecklist() {
        val root = view ?: return
        val repo = Repo(requireContext())
        val container = root.findViewById<LinearLayout>(R.id.subjectChecklist)
        container.removeAllViews()
        val subjects = repo.subjects()
        if (subjects.isEmpty()) {
            container.addView(
                TextView(requireContext(), null, 0, R.style.Hint).apply { text = "Add a subject first to share it." }
            )
            return
        }
        subjects.forEach { s ->
            container.addView(
                CheckBox(requireContext()).apply {
                    text = "${s.name} (${s.code})"
                    tag = s.id
                }
            )
        }
    }

    private fun generateQr() {
        val root = view ?: return
        val repo = Repo(requireContext())
        val container = root.findViewById<LinearLayout>(R.id.subjectChecklist)
        val selectedIds = (0 until container.childCount)
            .mapNotNull { container.getChildAt(it) as? CheckBox }
            .filter { it.isChecked }
            .map { it.tag as Long }

        if (selectedIds.isEmpty()) {
            toast("Pick at least one subject")
            return
        }

        val shared = selectedIds.map { id ->
            val s = repo.subject(id)!!
            val classes = repo.classesFor(id).map { SharedClass(it.day, it.start, it.end, it.type, it.room, it.online) }
            SharedSubject(s.name, s.code, s.professor, classes)
        }

        val size = (220 * resources.displayMetrics.density).toInt()
        val bitmap = try {
            BarcodeEncoder().encodeBitmap(buildSharePayload(shared), BarcodeFormat.QR_CODE, size, size)
        } catch (e: Exception) {
            toast("Too much to fit in one QR code — try sharing fewer subjects")
            return
        }
        lastQrBitmap = bitmap
        root.findViewById<ImageView>(R.id.ivQrResult).setImageBitmap(bitmap)
        root.findViewById<View>(R.id.qrCard).visibility = View.VISIBLE
    }

    private fun shareQrImage() {
        val bmp = lastQrBitmap ?: return
        try {
            val dir = File(requireContext().cacheDir, "qr").apply { mkdirs() }
            val file = File(dir, "acadex_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share QR code"))
        } catch (e: Exception) {
            toast("Could not share the QR code")
        }
    }

    private fun onScanResult(contents: String) {
        val root = view ?: return
        val tvError = root.findViewById<TextView>(R.id.tvImportError)
        val (error, subjects) = parseSharePayload(contents)
        if (error != null) {
            tvError.text = error
            tvError.visibility = View.VISIBLE
            clearImportPreview()
            return
        }
        tvError.visibility = View.GONE
        showImportPreview(subjects)
    }

    private fun showImportPreview(subjects: List<SharedSubject>) {
        val root = view ?: return
        val repo = Repo(requireContext())
        lastScanned = subjects
        previewChecks.clear()
        val container = root.findViewById<LinearLayout>(R.id.importPreview)
        container.removeAllViews()
        val existingByCode = repo.subjects().associateBy { it.code }
        val density = resources.displayMetrics.density

        subjects.forEachIndexed { si, s ->
            val existingSubject = existingByCode[s.code]
            container.addView(
                TextView(requireContext()).apply {
                    text = buildString {
                        append(s.name).append(" (").append(s.code).append(")")
                        if (!s.professor.isNullOrBlank()) append(" · ${s.professor}")
                        append(if (existingSubject != null) " — already in your subjects" else " — new subject")
                    }
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
                }
            )
            val existingClasses = existingSubject?.let { repo.classesFor(it.id) } ?: emptyList()

            s.classes.forEachIndexed { ci, c ->
                val isDup = existingClasses.any {
                    it.day == c.day && it.start == c.start && it.end == c.end && it.type == c.type &&
                        (it.room ?: "") == (c.room ?: "") && (it.online ?: "") == (c.online ?: "")
                }
                val conflict = if (!isDup) repo.classConflict(c.day, c.start, c.end) else null
                val label = "${c.day} ${c.start.timeLabel() ?: c.start}\u2013${c.end.timeLabel() ?: c.end} · " +
                    if (c.type == "Online") "Online · ${c.online}" else "Onsite · ${c.room}"
                val cb = CheckBox(requireContext())
                when {
                    isDup -> {
                        cb.text = "$label (already have this)"
                        cb.isChecked = false
                        cb.isEnabled = false
                    }
                    conflict != null -> {
                        cb.text = "$label \u26a0 conflicts with ${conflict.subjectName}"
                        cb.isChecked = false
                    }
                    else -> {
                        cb.text = label
                        cb.isChecked = true
                    }
                }
                previewChecks[si to ci] = cb
                container.addView(cb)
            }
        }
        root.findViewById<View>(R.id.importPreview).visibility = View.VISIBLE
        root.findViewById<View>(R.id.importActions).visibility = View.VISIBLE
    }

    private fun clearImportPreview() {
        val root = view ?: return
        lastScanned = null
        previewChecks.clear()
        root.findViewById<LinearLayout>(R.id.importPreview).removeAllViews()
        root.findViewById<View>(R.id.importPreview).visibility = View.GONE
        root.findViewById<View>(R.id.importActions).visibility = View.GONE
    }

    private fun confirmImport() {
        val subjects = lastScanned ?: return
        val repo = Repo(requireContext())
        val selected = previewChecks.filterValues { it.isChecked }.keys
        val outcome = repo.importShared(subjects, selected)
        toast(outcome.message)
        clearImportPreview()
        refreshSubjectChecklist()
    }
}
