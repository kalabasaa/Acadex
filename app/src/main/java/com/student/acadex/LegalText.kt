package com.student.acadex

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat

/** Turns a legal document into readable text: numbered section lines ("3. Title") become bold headings. */
fun renderLegal(context: Context, raw: String): CharSequence {
    val lines = raw.replace("\r", "").trimIndent().trim().lines()
    val heading = Regex("^\\d+\\. .+")
    val headingColor = ContextCompat.getColor(context, R.color.black) // follows Light / Dark Mode
    val out = SpannableStringBuilder()
    lines.forEachIndexed { i, line ->
        val start = out.length
        out.append(line)
        if (heading.matches(line)) {
            val end = out.length
            out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(headingColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(RelativeSizeSpan(1.15f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (i < lines.lastIndex) out.append('\n')
    }
    return out
}
