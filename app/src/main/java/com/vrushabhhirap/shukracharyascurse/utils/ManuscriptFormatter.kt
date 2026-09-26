package com.vrushabhhirap.shukracharyascurse.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Text and manuscript formatting utilities.
 * Handles hex chunking, devanagari display decoration, and formatted time stamps.
 */
object ManuscriptFormatter {

    /**
     * Formats a raw hex string into visually pleasing grouped blocks with line breaks.
     * E.g. "4142434445464748" -> "41 42 43 44  45 46 47 48"
     */
    fun formatHexDump(hexString: String, bytesPerLine: Int = 16): String {
        val clean = hexString.replace("\\s+".toRegex(), "")
        if (clean.isEmpty()) return ""

        val sb = StringBuilder()
        var i = 0
        var lineByteCount = 0

        while (i < clean.length) {
            val byteChunk = if (i + 2 <= clean.length) clean.substring(i, i + 2) else clean.substring(i)
            sb.append(byteChunk.uppercase(Locale.ROOT))
            lineByteCount++

            if (i + 2 < clean.length) {
                if (lineByteCount % bytesPerLine == 0) {
                    sb.append("\n")
                } else if (lineByteCount % 8 == 0) {
                    sb.append("   ")
                } else {
                    sb.append(" ")
                }
            }
            i += 2
        }

        return sb.toString()
    }

    /**
     * Decorates an archival title with ornamental border symbols.
     */
    fun decorateTitle(title: String): String {
        return "॥ $title ॥"
    }

    /**
     * Formats timestamp into Vedic/Gregorian hybrid display string.
     */
    fun formatArchiveDate(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val dateStr = sdf.format(Date(timestampMillis))
        return "Epoch: $dateStr"
    }

    /**
     * Truncates long manuscript text with an ellipsis while preserving whole words.
     */
    fun truncateExcerpt(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val cut = text.substring(0, maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > maxChars / 2) {
            cut.substring(0, lastSpace) + "…"
        } else {
            cut + "…"
        }
    }
}
