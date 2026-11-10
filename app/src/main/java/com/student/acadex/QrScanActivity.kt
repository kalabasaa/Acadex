package com.student.acadex

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.Size
import kotlin.math.min

/** Dimmed camera overlay with a centered square, corner guides and a short instruction. */
class ScanGuideView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 16 * resources.displayMetrics.scaledDensity
    }

    /** True while a QR code has just been read: the corners turn green. */
    var detected = false
        set(value) {
            field = value
            invalidate()
        }

    val side: Int get() = (min(width, height) * 0.7f).toInt()

    private fun box(): RectF {
        val s = side.toFloat()
        val l = (width - s) / 2f
        val t = (height - s) / 2f
        return RectF(l, t, l + s, t + s)
    }

    override fun onDraw(canvas: Canvas) {
        val r = box()
        val w = width.toFloat()
        val h = height.toFloat()
        // four dim rectangles around the box: the inside stays fully transparent
        canvas.drawRect(0f, 0f, w, r.top, dim)
        canvas.drawRect(0f, r.bottom, w, h, dim)
        canvas.drawRect(0f, r.top, r.left, r.bottom, dim)
        canvas.drawRect(r.right, r.top, w, r.bottom, dim)

        corner.color = ContextCompat.getColor(context, if (detected) R.color.success else R.color.white)
        val len = r.width() * 0.14f
        val i = corner.strokeWidth / 2f
        val l = r.left + i; val t = r.top + i; val rr = r.right - i; val b = r.bottom - i
        canvas.drawLine(l, t, l + len, t, corner); canvas.drawLine(l, t, l, t + len, corner)
        canvas.drawLine(rr, t, rr - len, t, corner); canvas.drawLine(rr, t, rr, t + len, corner)
        canvas.drawLine(l, b, l + len, b, corner); canvas.drawLine(l, b, l, b - len, corner)
        canvas.drawLine(rr, b, rr - len, b, corner); canvas.drawLine(rr, b, rr, b - len, corner)

        val text = "Align the QR code inside the frame."
        val maxW = w - 32 * density
        val measured = label.measureText(text)
        if (measured > maxW) label.textSize = label.textSize * maxW / measured
        canvas.drawText(text, w / 2f, r.bottom + 36 * density, label)
    }
}

/**
 * Portrait QR scanner. Returns the raw scanned text to the caller (EXTRA_TEXT); the existing
 * validation and import preview stay in ShareQrFragment.
 */
class QrScanActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TEXT = "qr_text"
    }

    private lateinit var scanner: DecoratedBarcodeView
    private lateinit var guide: ScanGuideView
    private lateinit var deniedPanel: View

    @Volatile private var handled = false
    private var cameraStarted = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showDenied()
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)
        scanner = findViewById(R.id.scanner)
        guide = findViewById(R.id.guide)
        deniedPanel = findViewById(R.id.deniedPanel)

        // we draw our own guide, so hide the library's laser line and status text
        scanner.viewFinder.visibility = View.GONE
        scanner.setStatusText("")
        scanner.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        // only decode what is inside the square box
        guide.post {
            val s = guide.side
            if (s > 0) scanner.barcodeView.framingRectSize = Size(s, s)
        }

        val btnClose = findViewById<View>(R.id.btnClose)
        val baseTop = (btnClose.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(btnClose) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = baseTop + bars.top }
            insets
        }
        btnClose.setOnClickListener { finish() }
        findViewById<View>(R.id.btnDeniedClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }

        if (hasCamera()) startCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        deniedPanel.visibility = View.GONE
        cameraStarted = true
        scanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                val text = result.text
                // the same code is reported many times a second: take only the first
                if (handled || text.isNullOrEmpty()) return
                handled = true
                guide.detected = true // visual feedback
                scanner.pause()
                guide.postDelayed({
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
                    finish()
                }, 350)
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
        scanner.resume()
    }

    private fun showDenied() {
        cameraStarted = false
        deniedPanel.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (handled) return
        if (hasCamera()) {
            // also covers coming back from the system settings after granting the permission
            if (!cameraStarted) startCamera() else scanner.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        scanner.pause() // releases the camera whenever the scanner is not on screen
    }
}
