package com.mlb.scoreboard.renderer

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parser and renderer for BDF (Bitmap Distribution Format) fonts.
 * These are the same pixel fonts used on the LED matrix hardware.
 */
class BdfFont private constructor(
    val name: String,
    val charWidth: Int,
    val charHeight: Int,
    val baseline: Int,
    private val glyphs: Map<Int, BdfGlyph>
) {
    data class BdfGlyph(
        val encoding: Int,
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val dWidthX: Int, // advance width
        val bitmap: List<ByteArray>
    )

    /**
     * Get the pixel width of a string rendered in this font.
     */
    fun textWidth(text: String): Int {
        var width = 0
        for (ch in text) {
            val glyph = glyphs[ch.code]
            if (glyph != null) {
                width += glyph.dWidthX
            } else {
                width += charWidth // fallback
            }
        }
        return width
    }

    /**
     * Render a single character onto the matrix via the pixel callback.
     * Returns the advance width (how far to move X for the next char).
     */
    fun drawChar(ch: Char, x: Int, y: Int, setPixel: (Int, Int) -> Unit): Int {
        val glyph = glyphs[ch.code] ?: return charWidth

        for (row in 0 until glyph.height) {
            val bitmapRow = if (row < glyph.bitmap.size) glyph.bitmap[row] else continue
            for (col in 0 until glyph.width) {
                val byteIndex = col / 8
                val bitIndex = 7 - (col % 8)
                if (byteIndex < bitmapRow.size) {
                    if ((bitmapRow[byteIndex].toInt() and (1 shl bitIndex)) != 0) {
                        val px = x + col + glyph.xOffset
                        // BDF y-offset: glyph is drawn relative to baseline
                        // y parameter is the baseline position
                        val py = y - glyph.height - glyph.yOffset + row
                        setPixel(px, py)
                    }
                }
            }
        }
        return glyph.dWidthX
    }

    /**
     * Render a string of text onto the matrix.
     * x, y: position where y is the baseline of the text.
     */
    fun drawText(text: String, x: Int, y: Int, setPixel: (Int, Int) -> Unit): Int {
        var curX = x
        for (ch in text) {
            curX += drawChar(ch, curX, y, setPixel)
        }
        return curX - x // total width drawn
    }

    companion object {
        private val cache = mutableMapOf<String, BdfFont>()

        fun load(context: Context, fontName: String): BdfFont {
            cache[fontName]?.let { return it }

            val fileName = "$fontName.bdf"
            val reader = BufferedReader(
                InputStreamReader(context.assets.open("fonts/$fileName"))
            )

            var globalWidth = 0
            var globalHeight = 0
            var globalBaseline = 0
            val glyphs = mutableMapOf<Int, BdfGlyph>()

            // Parsing state
            var inChar = false
            var inBitmap = false
            var encoding = 0
            var bbxW = 0
            var bbxH = 0
            var bbxXOff = 0
            var bbxYOff = 0
            var dWidthX = 0
            var bitmapRows = mutableListOf<ByteArray>()

            reader.forEachLine { rawLine ->
                val line = rawLine.trim()

                when {
                    line.startsWith("FONTBOUNDINGBOX ") -> {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 5) {
                            globalWidth = parts[1].toIntOrNull() ?: 0
                            globalHeight = parts[2].toIntOrNull() ?: 0
                            globalBaseline = parts[4].toIntOrNull() ?: 0
                        }
                    }
                    line == "STARTCHAR" || line.startsWith("STARTCHAR ") -> {
                        inChar = true
                        encoding = 0
                        bbxW = globalWidth
                        bbxH = globalHeight
                        bbxXOff = 0
                        bbxYOff = 0
                        dWidthX = globalWidth
                        bitmapRows = mutableListOf()
                    }
                    line.startsWith("ENCODING ") && inChar -> {
                        encoding = line.substringAfter("ENCODING ").trim().toIntOrNull() ?: 0
                    }
                    line.startsWith("DWIDTH ") && inChar -> {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            dWidthX = parts[1].toIntOrNull() ?: globalWidth
                        }
                    }
                    line.startsWith("BBX ") && inChar -> {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 5) {
                            bbxW = parts[1].toIntOrNull() ?: 0
                            bbxH = parts[2].toIntOrNull() ?: 0
                            bbxXOff = parts[3].toIntOrNull() ?: 0
                            bbxYOff = parts[4].toIntOrNull() ?: 0
                        }
                    }
                    line == "BITMAP" && inChar -> {
                        inBitmap = true
                    }
                    line == "ENDCHAR" -> {
                        if (inChar && encoding in 32..126) {
                            glyphs[encoding] = BdfGlyph(
                                encoding = encoding,
                                width = bbxW,
                                height = bbxH,
                                xOffset = bbxXOff,
                                yOffset = bbxYOff,
                                dWidthX = dWidthX,
                                bitmap = bitmapRows.toList()
                            )
                        }
                        inChar = false
                        inBitmap = false
                    }
                    inBitmap && inChar -> {
                        // Parse hex bitmap row
                        try {
                            val bytes = line.chunked(2).map {
                                it.toInt(16).toByte()
                            }.toByteArray()
                            bitmapRows.add(bytes)
                        } catch (_: Exception) {
                            // Skip malformed bitmap lines
                        }
                    }
                }
            }

            reader.close()

            val font = BdfFont(
                name = fontName,
                charWidth = globalWidth,
                charHeight = globalHeight,
                baseline = globalBaseline,
                glyphs = glyphs
            )
            cache[fontName] = font
            return font
        }

        fun clearCache() {
            cache.clear()
        }
    }
}
