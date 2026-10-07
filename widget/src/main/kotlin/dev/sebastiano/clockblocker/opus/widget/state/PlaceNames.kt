package dev.sebastiano.clockblocker.opus.widget.state

/**
 * Shorter forms of a place name, for widget text that has to fit a small cell. Bundled cities can be long ("Qian
 * Gorlos Mongol Autonomous County", "Fayetteville/Springdale/Rogers"), and no layout can promise to fit any length.
 * So the widgets fit the first form that fits whole (see `LabelFit`), and screen readers always get the full name.
 */
object PlaceNames {
    /** Where a name is cut for its short form: the first of these, e.g. "Fayetteville/Springdale/Rogers". */
    private val CUTS = listOf("/", " - ", "(")

    /**
     * [full], then [full] cut at its first "/", " - " or "(" ("Fayetteville"), then the airport's IATA [code]
     * ("XNA"), without repeats or blanks. The full name always comes first.
     */
    fun options(full: String, code: String?): List<String> {
        val cut = CUTS.mapNotNull { full.indexOf(it).takeIf { i -> i > 0 } }.minOrNull()?.let { full.substring(0, it).trim() }
        return listOfNotNull(full, cut, code).filter { it.isNotBlank() }.distinct()
    }
}
