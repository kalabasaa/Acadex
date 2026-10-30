package com.student.acadex

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Fragment.argId: Long get() = arguments?.getLong("id") ?: 0L
val Fragment.argSubjectId: Long get() = arguments?.getLong("subjectId") ?: 0L

fun Fragment.toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

// slide in when opening a page, slide back out when leaving it
fun pageNavOptions(): NavOptions = NavOptions.Builder()
    .setEnterAnim(R.anim.slide_in_right)
    .setExitAnim(R.anim.slide_out_left)
    .setPopEnterAnim(R.anim.slide_in_left)
    .setPopExitAnim(R.anim.slide_out_right)
    .build()

fun Fragment.go(dest: Int, id: Long = 0, subjectId: Long = 0) =
    findNavController().navigate(dest, bundleOf("id" to id, "subjectId" to subjectId), pageNavOptions())

fun Fragment.back() {
    findNavController().popBackStack()
}

fun Fragment.confirm(msg: String, yes: String = "Delete", title: String? = null, onYes: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(msg)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(yes) { _, _ -> onYes() }
        .show()
}

fun Fragment.copyToClipboard(label: String, text: String) {
    val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Fragment.clipboardText(): String? {
    val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(requireContext())?.toString()
}

fun <T> LinearLayout.fill(items: List<T>, layout: Int, bind: (View, T) -> Unit) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    if (items.isEmpty()) {
        addView(inflater.inflate(R.layout.item_empty, this, false))
        return
    }
    items.forEach { item -> addView(inflater.inflate(layout, this, false).also { bind(it, item) }) }
}

fun View.bindRow(left: String?, title: String, sub: String?, right: String? = null, onClick: () -> Unit) {
    findViewById<TextView>(R.id.tvLeft).apply {
        text = left
        visibility = if (left == null) View.GONE else View.VISIBLE
    }
    findViewById<TextView>(R.id.tvTitle).text = title
    findViewById<TextView>(R.id.tvSub).apply {
        text = sub
        visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
    findViewById<TextView>(R.id.tvRight).apply {
        text = right
        visibility = if (right == null) View.GONE else View.VISIBLE
    }
    setOnClickListener { onClick() }
}

fun EditText.pickDate() {
    setOnClickListener {
        val c = Calendar.getInstance()
        text.toString().split("-").takeIf { it.size == 3 }?.let { c.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt()) }
        DatePickerDialog(
            context,
            { _, y, m, d -> setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

fun EditText.pickTime() {
    setOnClickListener {
        val c = Calendar.getInstance()
        text.toString().split(":").takeIf { it.size == 2 }?.let {
            c.set(Calendar.HOUR_OF_DAY, it[0].toInt())
            c.set(Calendar.MINUTE, it[1].toInt())
        }
        TimePickerDialog(
            context,
            { _, h, m -> setText(String.format(Locale.US, "%02d:%02d", h, m)) },
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true
        ).show()
    }
}

fun String?.shortDate(): String = try {
    SimpleDateFormat("MMM d", Locale.getDefault())
        .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this!!.take(10))!!)
} catch (e: Exception) {
    "No date"
}

// A task's due_date is "yyyy-MM-dd" or "yyyy-MM-dd HH:mm".
fun String?.dateOnly(): String? = this?.takeIf { it.length >= 10 }?.substring(0, 10)

fun String?.timeOnly(): String? = this?.takeIf { it.length >= 16 }?.substring(11, 16)

fun String?.timeLabel(): String? = try {
    SimpleDateFormat("h:mm a", Locale.getDefault())
        .format(SimpleDateFormat("HH:mm", Locale.US).parse(this!!)!!)
} catch (e: Exception) {
    null
}

fun Long.fmt(): String = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(this))

fun ClassItem.where(): String = if (type == "Online") "Online · $online" else "Onsite · $room"

/** "Completed" (what the edit form saves) is shown as "Finished". */
fun String.statusLabel(): String = if (this == "Completed") "Finished" else this

fun Context.statusColor(status: String): Int = ContextCompat.getColor(
    this,
    when (status.statusLabel()) {
        "Finished" -> R.color.success
        "Missed" -> R.color.danger
        else -> R.color.muted
    }
)

/** Binds a task row (item_task): sub code, date/time, title, short description, status. */
fun View.bindTask(a: ActivityItem, onOpen: () -> Unit) {
    findViewById<TextView>(R.id.tvCode).text = a.subjectCode?.takeIf { it.isNotBlank() } ?: "—"
    findViewById<TextView>(R.id.tvDate).text = a.dueDate.shortDate()
    findViewById<TextView>(R.id.tvTime).text =
        if (a.dueDate == null) "" else (a.dueDate.timeOnly().timeLabel() ?: "All day")
    findViewById<TextView>(R.id.tvTitle).text = a.title
    findViewById<TextView>(R.id.tvStatus).apply {
        text = a.status.statusLabel()
        setTextColor(context.statusColor(a.status))
    }
    findViewById<TextView>(R.id.tvDesc).apply {
        val desc = a.description?.trim().orEmpty()
        text = desc
        visibility = if (desc.isEmpty()) View.GONE else View.VISIBLE
    }
    setOnClickListener { onOpen() }
}
