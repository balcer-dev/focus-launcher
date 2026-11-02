package com.focus.launcher.data

/**
 * These keys with [key] and its neighbour in [shown] (the order on screen) swapped: the one above
 * for a negative [delta], the one below for a positive one. Keys that are not on screen, of apps
 * hidden, uninstalled or in the other profile, keep their places. Unchanged when [key] is not
 * shown, is already at that end, or either key is missing here.
 */
fun List<String>.movedAmong(shown: List<String>, key: String, delta: Int): List<String> {
    val at = shown.indexOf(key)
    if (at < 0 || delta == 0) return this
    val neighbour = shown.getOrNull(if (delta < 0) at - 1 else at + 1) ?: return this
    val from = indexOf(key)
    val to = indexOf(neighbour)
    if (from < 0 || to < 0) return this
    return toMutableList().apply {
        this[from] = neighbour
        this[to] = key
    }
}
