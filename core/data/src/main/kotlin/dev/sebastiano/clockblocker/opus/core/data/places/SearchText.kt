package dev.sebastiano.clockblocker.opus.core.data.places

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for matching: lower case, diacritics removed ("Zürich" -> "zurich", "São Paulo" -> "sao paulo"),
 * a few letters without a decomposition mapped by hand ("ß" -> "ss", "ø" -> "o", "ł" -> "l"…), and every run
 * of punctuation/whitespace collapsed to one space ("Dallas-Fort Worth" -> "dallas fort worth").
 */
object SearchText {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val specials = mapOf(
        'ß' to "ss", 'ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ł' to "l", 'đ' to "d", 'ð' to "d",
        'þ' to "th", 'ı' to "i", 'ħ' to "h", 'ŧ' to "t", 'ŀ' to "l",
    )

    fun fold(text: String): String {
        if (text.isEmpty()) return text
        val lower = text.lowercase(Locale.ROOT)
        val decomposed = if (lower.all { it.code < 128 }) {
            lower // Fast path: most of the dataset is plain ASCII.
        } else {
            Normalizer.normalize(lower, Normalizer.Form.NFD).replace(combiningMarks, "")
        }
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (c in decomposed) {
            val mapped = specials[c]
            when {
                mapped != null -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.append(mapped)
                }
                c.isLetterOrDigit() -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.append(c)
                }
                // Apostrophes join words: "O'Hare" -> "ohare", "Xi'an" -> "xian".
                c == '\'' || c == '’' -> Unit
                else -> pendingSpace = true
            }
        }
        return out.toString()
    }

    /** [fold]ed text split into words. */
    fun words(text: String): List<String> = fold(text).split(' ').filter { it.isNotEmpty() }
}
