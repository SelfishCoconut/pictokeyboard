package org.pictokeyboard.ui

/**
 * Returns [list] with the item matching [match] swapped one step toward the
 * start ([up]) or the end, or null when it cannot move -- no match, or already
 * at that end. Callers treat null as "nothing to persist".
 */
fun <T> movedBy(list: List<T>, match: (T) -> Boolean, up: Boolean): List<T>? {
    val i = list.indexOfFirst(match)
    if (i < 0) return null
    val j = if (up) i - 1 else i + 1
    if (j !in list.indices) return null
    return list.toMutableList().apply { this[i] = this[j].also { this[j] = this[i] } }
}
