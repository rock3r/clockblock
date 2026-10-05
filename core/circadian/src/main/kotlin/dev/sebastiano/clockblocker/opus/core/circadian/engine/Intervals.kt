package dev.sebastiano.clockblocker.opus.core.circadian.engine

/** Half-open interval [start, end) in UTC hours from the plan's reference midnight. */
internal data class Interval(val start: Double, val end: Double) {
    val length: Double get() = end - start

    operator fun contains(t: Double): Boolean = t >= start && t < end

    fun overlaps(other: Interval): Boolean = start < other.end && other.start < end

    fun intersect(other: Interval): Interval? {
        val a = maxOf(start, other.start)
        val b = minOf(end, other.end)
        return if (b > a) Interval(a, b) else null
    }
}

internal const val EPS: Double = 1e-6

/** Port of `planner.subtract`: removes every cut from [iv]; drops slivers ≤ 1e-6 h. Order is preserved. */
internal fun subtract(iv: Interval, cuts: List<Interval>): List<Interval> {
    var out = listOf(iv)
    for (c in cuts) {
        val next = ArrayList<Interval>(out.size + 1)
        for (p in out) {
            if (c.end <= p.start || c.start >= p.end) {
                next += p
                continue
            }
            if (p.start < c.start) next += Interval(p.start, c.start)
            if (c.end < p.end) next += Interval(c.end, p.end)
        }
        out = next
    }
    return out.filter { it.end - it.start > EPS }
}

internal fun List<Interval>.minus(cuts: List<Interval>): List<Interval> = flatMap { subtract(it, cuts) }

/** Sorted union of overlapping or touching intervals. */
internal fun List<Interval>.union(): List<Interval> {
    if (isEmpty()) return this
    val sorted = sortedBy { it.start }
    val out = ArrayList<Interval>()
    var cur = sorted.first()
    for (iv in sorted.drop(1)) {
        cur = if (iv.start <= cur.end) Interval(cur.start, maxOf(cur.end, iv.end)) else {
            out += cur
            iv
        }
    }
    out += cur
    return out
}

internal fun List<Interval>.containsPoint(t: Double): Boolean = any { t in it }
