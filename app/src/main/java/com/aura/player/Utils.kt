package com.aura.player

/** Pure helpers (JVM-testable, no Android dependencies). */

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Late night"
}

/** Returns a copy of the list with the element at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val m = toMutableList()
    val item = m.removeAt(from)
    m.add(to, item)
    return m
}

/** Stable key identifying an album independent of MediaStore ids (SAF tracks have none). */
fun albumKeyOf(albumId: Long, album: String, artist: String): String =
    if (albumId > 0) "ms:$albumId" else "saf:${album.lowercase()}|${artist.lowercase()}"

fun cleanTag(value: String?, fallback: String): String {
    val v = value?.trim().orEmpty()
    return if (v.isEmpty() || v == "<unknown>") fallback else v
}

/** MediaStore sometimes encodes disc*1000 + track. */
fun normalizeTrackNumber(raw: Int): Int = if (raw > 1000) raw % 1000 else raw

/** Maps a hue seed from a string so placeholders are stable per title. */
fun hueFor(seed: String): Float = ((seed.hashCode().toLong() and 0x7fffffff) % 360).toFloat()
