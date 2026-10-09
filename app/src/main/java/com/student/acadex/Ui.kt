package com.student.acadex

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Fragment.argId: Long get() = arguments?.getLong("id") ?: 0L
val Fragment.argSubjectId: Long get() = arguments?.getLong("subjectId") ?: 0L

fun Fragment.toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

fun Fragment.go(dest: Int, id: Long = 0, subjectId: Long = 0) =
    findNavController().navigate(dest, bundleOf("id" to id, "subjectId" to subjectId))

fun Fragment.back() {
    findNavController().popBackStack()
}

fun Fragment.confirm(msg: String, onYes: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setMessage(msg)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete") { _, _ -> onYes() }
        .show()
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
        .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this!!)!!)
} catch (e: Exception) {
    "No date"
}

fun Long.fmt(): String = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(this))

fun ClassItem.where(): String = if (type == "Online") "Online · $online" else "Onsite · $room"
