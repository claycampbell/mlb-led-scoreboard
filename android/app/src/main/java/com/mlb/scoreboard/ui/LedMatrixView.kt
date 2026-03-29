package com.mlb.scoreboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Custom view that renders a virtual LED matrix display.
 * Each "pixel" is drawn as a small rounded rectangle with slight gaps
 * to simulate the look of individual LEDs on an RGB matrix panel.
 */
class LedMatrixView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MATRIX_WIDTH = 64
        const val MATRIX_HEIGHT = 32
        private const val LED_GAP_RATIO = 0.15f // gap between LEDs as fraction of pixel size
        private const val LED_CORNER_RATIO = 0.2f // corner radius as fraction of pixel size
    }

    // The virtual framebuffer: [y][x] = ARGB color
    private val framebuffer = Array(MATRIX_HEIGHT) { IntArray(MATRIX_WIDTH) }
    private val backBuffer = Array(MATRIX_HEIGHT) { IntArray(MATRIX_WIDTH) }

    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint().apply {
        color = Color.rgb(2, 4, 8) // very dark, almost black
        style = Paint.Style.FILL
    }
    private val ledRect = RectF()

    // Calculated layout values
    private var pixelSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var ledGap = 0f
    private var ledCorner = 0f

    // Background color for the matrix (dark blue from original)
    var matrixBackgroundColor = Color.rgb(7, 14, 25)

    // Tap listener for settings access
    var onDoubleTapListener: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleTapListener?.invoke()
                return true
            }
        }
    )

    init {
        // Start with background color
        clearBuffer(matrixBackgroundColor)
        keepScreenOn = true
    }

    fun clearBuffer(color: Int = matrixBackgroundColor) {
        synchronized(backBuffer) {
            for (y in 0 until MATRIX_HEIGHT) {
                for (x in 0 until MATRIX_WIDTH) {
                    backBuffer[y][x] = color
                }
            }
        }
    }

    fun setPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until MATRIX_WIDTH && y in 0 until MATRIX_HEIGHT) {
            synchronized(backBuffer) {
                backBuffer[y][x] = color
            }
        }
    }

    fun setPixelRGB(x: Int, y: Int, r: Int, g: Int, b: Int) {
        setPixel(x, y, Color.rgb(r, g, b))
    }

    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        // Bresenham's line algorithm
        var dx = Math.abs(x1 - x0)
        var dy = -Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var cx = x0
        var cy = y0

        while (true) {
            setPixel(cx, cy, color)
            if (cx == x1 && cy == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                if (cx == x1) break
                err += dy
                cx += sx
            }
            if (e2 <= dx) {
                if (cy == y1) break
                err += dx
                cy += sy
            }
        }
    }

    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        for (py in y until y + height) {
            for (px in x until x + width) {
                setPixel(px, py, color)
            }
        }
    }

    /**
     * Draw a filled diamond (base shape rotated 45 degrees) for bases display
     */
    fun drawDiamond(cx: Int, cy: Int, size: Int, color: Int, filled: Boolean = true) {
        val half = size / 2
        if (filled) {
            for (row in 0 until size) {
                val dist = if (row <= half) row else size - 1 - row
                for (col in half - dist..half + dist) {
                    setPixel(cx + col - half, cy + row - half, color)
                }
            }
        } else {
            // Draw outline only
            for (i in 0..half) {
                setPixel(cx + i, cy - half + i, color)
                setPixel(cx - i, cy - half + i, color)
                setPixel(cx + i, cy + half - i, color)
                setPixel(cx - i, cy + half - i, color)
            }
        }
    }

    /**
     * Draw a small filled circle for outs indicators
     */
    fun drawCircle(cx: Int, cy: Int, radius: Int, color: Int, filled: Boolean = true) {
        for (y in -radius..radius) {
            for (x in -radius..radius) {
                if (filled) {
                    if (x * x + y * y <= radius * radius) {
                        setPixel(cx + x, cy + y, color)
                    }
                } else {
                    val d = x * x + y * y
                    if (d <= radius * radius && d >= (radius - 1) * (radius - 1)) {
                        setPixel(cx + x, cy + y, color)
                    }
                }
            }
        }
    }

    /**
     * Draw an upward-pointing triangle (top of inning)
     */
    fun drawTriangleUp(x: Int, y: Int, size: Int, color: Int) {
        for (row in 0 until size) {
            val width = row * 2 + 1
            val startX = x + size - 1 - row
            for (col in 0 until width) {
                setPixel(startX + col, y + size - 1 - row, color)
            }
        }
    }

    /**
     * Draw a downward-pointing triangle (bottom of inning)
     */
    fun drawTriangleDown(x: Int, y: Int, size: Int, color: Int) {
        for (row in 0 until size) {
            val width = row * 2 + 1
            val startX = x + size - 1 - row
            for (col in 0 until width) {
                setPixel(startX + col, y + row, color)
            }
        }
    }

    /**
     * Swap the back buffer to the front and trigger a redraw.
     * Call this after finishing a frame of rendering.
     */
    fun swapBuffers() {
        synchronized(backBuffer) {
            for (y in 0 until MATRIX_HEIGHT) {
                System.arraycopy(backBuffer[y], 0, framebuffer[y], 0, MATRIX_WIDTH)
            }
        }
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate pixel size to fill the view while maintaining aspect ratio
        val scaleX = w.toFloat() / MATRIX_WIDTH
        val scaleY = h.toFloat() / MATRIX_HEIGHT
        pixelSize = minOf(scaleX, scaleY)
        ledGap = pixelSize * LED_GAP_RATIO
        ledCorner = pixelSize * LED_CORNER_RATIO

        // Center the matrix in the view
        offsetX = (w - MATRIX_WIDTH * pixelSize) / 2f
        offsetY = (h - MATRIX_HEIGHT * pixelSize) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fill background
        canvas.drawColor(bgPaint.color)

        val ledSize = pixelSize - ledGap

        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                val color = framebuffer[y][x]

                // Skip fully black pixels for performance (they blend with bg)
                if (color == Color.BLACK || color == bgPaint.color) continue

                ledRect.set(
                    offsetX + x * pixelSize + ledGap / 2f,
                    offsetY + y * pixelSize + ledGap / 2f,
                    offsetX + x * pixelSize + ledSize + ledGap / 2f,
                    offsetY + y * pixelSize + ledSize + ledGap / 2f
                )

                ledPaint.color = color
                canvas.drawRoundRect(ledRect, ledCorner, ledCorner, ledPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }
}
