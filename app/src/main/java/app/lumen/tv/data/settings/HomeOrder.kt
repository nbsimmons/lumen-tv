package app.lumen.tv.data.settings

fun <T> List<T>.inHomeOrder(saved: List<String>, key: (T) -> String): List<T> {
    if (saved.isEmpty()) return this
    val rank = saved.withIndex().associate { (index, value) -> value to index }
    val defaultIndex = withIndex().associate { (index, item) -> key(item) to index }
    return sortedWith(compareBy({ rank[key(it)] ?: (saved.size + (defaultIndex[key(it)] ?: 0)) }, { defaultIndex[key(it)] ?: 0 }))
}

fun List<String>.moved(key: String, delta: Int): List<String> {
    val index = indexOf(key)
    if (index < 0) return this
    val target = (index + delta).coerceIn(0, lastIndex)
    if (target == index) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
