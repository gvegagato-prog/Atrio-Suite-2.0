package com.gonzalo.gamerate

// Lo que ocupa cada ficha en un calendario (del día que empezó al que terminó): lo usa el widget del Diario.

/** s y e: primer y último día que ocupa en el calendario (días desde 1970). */
class CalItem(
    val id: Long, val name: String, val cover: String,
    val s: Long, val e: Long, val realEnd: Long, val isPlay: Boolean,
    val rated: Boolean = false, val score: Int = 0,
    /** De qué categoría es (en el Diario con «Todo» se mezclan). */
    val kind: Int = -1
)

fun calItems(games: List<Game>, plays: List<Play>, kind: Int = -1): List<CalItem> {
    val t = today()
    val out = ArrayList<CalItem>()
    fun add(id: Long, name: String, cover: String, start: Long, end: Long, isPlay: Boolean, score: Int = -1) {
        if (start <= 0L && end <= 0L) return
        val s = if (start > 0L) start else end
        val e = if (end > 0L) maxOf(end, s) else maxOf(t, s)
        out.add(CalItem(id, name, cover, s, e, end, isPlay, score >= 0, maxOf(score, 0), kind))
    }
    plays.forEach { add(it.id, it.name, it.cover, it.start, it.end, true) }
    games.forEach { add(it.id, it.name, it.cover, it.start, it.end, false, it.general) }
    return out
}
