package com.mlb.scoreboard.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.mlb.scoreboard.data.models.PlayEvent

class StrikeZoneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(16, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val pitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pitchBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val batterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val batterOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var pitches: List<PlayEvent> = emptyList()
    private var batSide: String = "R"
    private var strikeZoneTop: Double = 3.5
    private var strikeZoneBottom: Double = 1.5

    // Zone dimensions in feet — plate is 17 inches (1.417 ft) wide
    private val plateWidthFt = 1.417f
    private val zoneMargin = 0.5f // extra space around zone

    fun setPitchData(
        pitches: List<PlayEvent>,
        batSide: String,
        strikeZoneTop: Double,
        strikeZoneBottom: Double
    ) {
        this.pitches = pitches
        this.batSide = batSide
        this.strikeZoneTop = strikeZoneTop
        this.strikeZoneBottom = strikeZoneBottom
        invalidate()
    }

    fun clearPitches() {
        pitches = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val padding = w * 0.08f

        // Compute zone rectangle in view coordinates
        val zoneWidthPx = w * 0.42f
        val zoneHeightPx = h * 0.48f
        val zoneLeft = centerX - zoneWidthPx / 2f
        val zoneRight = centerX + zoneWidthPx / 2f
        val zoneTop = h * 0.15f
        val zoneBottom = zoneTop + zoneHeightPx

        // Draw batter silhouette
        drawBatter(canvas, zoneLeft, zoneRight, zoneTop, zoneBottom)

        // Draw home plate
        drawHomePlate(canvas, centerX, zoneBottom + h * 0.08f)

        // Draw strike zone box
        canvas.drawRect(zoneLeft, zoneTop, zoneRight, zoneBottom, zoneFillPaint)
        canvas.drawRect(zoneLeft, zoneTop, zoneRight, zoneBottom, zonePaint)

        // Draw 3x3 grid lines
        val thirdW = zoneWidthPx / 3f
        val thirdH = zoneHeightPx / 3f
        for (i in 1..2) {
            canvas.drawLine(zoneLeft + thirdW * i, zoneTop, zoneLeft + thirdW * i, zoneBottom, gridPaint)
            canvas.drawLine(zoneLeft, zoneTop + thirdH * i, zoneRight, zoneTop + thirdH * i, gridPaint)
        }

        // Draw pitches
        val pitchRadius = w * 0.032f
        speedPaint.textSize = w * 0.055f
        textPaint.textSize = w * 0.045f

        val szTop = strikeZoneTop.toFloat()
        val szBot = strikeZoneBottom.toFloat()
        val szHeight = szTop - szBot
        val halfPlate = plateWidthFt / 2f

        pitches.forEachIndexed { index, pitch ->
            val coords = pitch.pitchData?.coordinates ?: return@forEachIndexed
            val pX = coords.pX?.toFloat() ?: return@forEachIndexed
            val pZ = coords.pZ?.toFloat() ?: return@forEachIndexed

            // Convert feet to view coordinates
            val viewX = centerX + (pX / halfPlate) * (zoneWidthPx / 2f)
            val viewY = zoneTop + ((szTop - pZ) / szHeight) * zoneHeightPx

            val color = getPitchColor(pitch.details?.type?.code)
            pitchPaint.color = color

            val isLatest = index == pitches.lastIndex
            val radius = if (isLatest) pitchRadius * 1.3f else pitchRadius
            val alpha = if (isLatest) 255 else 180
            pitchPaint.alpha = alpha

            canvas.drawCircle(viewX, viewY, radius, pitchPaint)

            if (isLatest) {
                pitchBorderPaint.color = Color.WHITE
                canvas.drawCircle(viewX, viewY, radius, pitchBorderPaint)

                // Draw speed label for latest pitch
                val speed = pitch.pitchData?.startSpeed
                if (speed != null) {
                    val speedText = "${speed.toInt()}"
                    speedPaint.textSize = w * 0.09f
                    speedPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(speedText, centerX, zoneBottom + h * 0.22f, speedPaint)
                    speedPaint.textSize = w * 0.05f
                    speedPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("MPH", centerX, zoneBottom + h * 0.29f, speedPaint)
                }

                // Pitch type label
                val typeDesc = pitch.details?.type?.description ?: ""
                if (typeDesc.isNotEmpty()) {
                    textPaint.textSize = w * 0.055f
                    textPaint.color = color
                    canvas.drawText(typeDesc, centerX, h * 0.95f, textPaint)
                    textPaint.color = Color.WHITE
                }
            } else {
                // Pitch number inside dot
                textPaint.textSize = pitchRadius * 1.1f
                textPaint.color = Color.WHITE
                canvas.drawText("${index + 1}", viewX, viewY + pitchRadius * 0.35f, textPaint)
            }
        }
    }

    private fun drawBatter(canvas: Canvas, zoneLeft: Float, zoneRight: Float, zoneTop: Float, zoneBottom: Float) {
        val isLefty = batSide == "L"
        val batterX = if (isLefty) zoneRight + width * 0.06f else zoneLeft - width * 0.06f

        val headCY = zoneTop - height * 0.02f
        val headR = width * 0.04f

        // Head
        canvas.drawCircle(batterX, headCY, headR, batterPaint)
        canvas.drawCircle(batterX, headCY, headR, batterOutlinePaint)

        // Helmet brim
        val brimDir = if (isLefty) -1f else 1f
        val brimPath = Path().apply {
            moveTo(batterX + brimDir * headR * 0.3f, headCY - headR * 0.6f)
            lineTo(batterX + brimDir * headR * 1.8f, headCY - headR * 0.8f)
            lineTo(batterX + brimDir * headR * 1.8f, headCY - headR * 0.3f)
            close()
        }
        canvas.drawPath(brimPath, batterPaint)

        // Body (torso)
        val bodyTop = headCY + headR
        val bodyBottom = zoneBottom + height * 0.02f
        val bodyW = width * 0.035f

        val bodyPath = Path().apply {
            moveTo(batterX - bodyW, bodyTop)
            lineTo(batterX + bodyW, bodyTop)
            lineTo(batterX + bodyW * 1.2f, bodyBottom)
            lineTo(batterX - bodyW * 1.2f, bodyBottom)
            close()
        }
        canvas.drawPath(bodyPath, batterPaint)
        canvas.drawPath(bodyPath, batterOutlinePaint)

        // Bat
        val batPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 200, 170, 100)
            strokeWidth = width * 0.012f
            strokeCap = Paint.Cap.ROUND
        }
        val batStartX = batterX + brimDir * bodyW * 0.5f
        val batStartY = headCY + headR * 1.5f
        val batEndX = batterX - brimDir * width * 0.12f
        val batEndY = headCY - headR * 2.5f
        canvas.drawLine(batStartX, batStartY, batEndX, batEndY, batPaint)
    }

    private fun drawHomePlate(canvas: Canvas, centerX: Float, plateY: Float) {
        val plateW = width * 0.08f
        val plateH = width * 0.04f
        val path = Path().apply {
            moveTo(centerX - plateW / 2f, plateY)
            lineTo(centerX + plateW / 2f, plateY)
            lineTo(centerX + plateW / 2f, plateY + plateH * 0.6f)
            lineTo(centerX, plateY + plateH)
            lineTo(centerX - plateW / 2f, plateY + plateH * 0.6f)
            close()
        }
        canvas.drawPath(path, platePaint)
    }

    companion object {
        fun getPitchColor(typeCode: String?): Int {
            return when (typeCode) {
                "FF", "FA" -> Color.rgb(239, 83, 80)    // Fastball - red
                "SI" -> Color.rgb(171, 71, 188)          // Sinker - purple
                "FC" -> Color.rgb(255, 112, 67)          // Cutter - orange
                "SL" -> Color.rgb(255, 202, 40)          // Slider - yellow
                "CU", "KC" -> Color.rgb(102, 187, 106)   // Curveball - green
                "CH" -> Color.rgb(66, 165, 245)           // Changeup - blue
                "ST" -> Color.rgb(236, 64, 122)           // Sweeper - pink
                "FS", "FO" -> Color.rgb(38, 198, 218)    // Splitter - teal
                "KN" -> Color.rgb(156, 204, 101)          // Knuckleball - lime
                else -> Color.rgb(120, 144, 156)          // Other - gray
            }
        }
    }
}
