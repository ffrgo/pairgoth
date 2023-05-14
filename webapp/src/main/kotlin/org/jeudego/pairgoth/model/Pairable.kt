package org.jeudego.pairgoth.model

sealed class Pairable(val id: Int, val name: String, val rating: Double, val rank: Int) {
}

fun Pairable.displayRank(): String = when {
    rank < 0 -> "${-rank}k"
    rank >= 0 && rank < 10 -> "${rank + 1}d"
    rank >= 10 -> "${rank - 9}p"
    else -> throw Error("impossible")
}

private val rankRegex = Regex("(\\d+)([kdp])", RegexOption.IGNORE_CASE)

fun Pairable.setRank(rankStr: String): Int {
    val (level, letter) = rankRegex.matchEntire(rankStr)?.destructured ?: throw Error("invalid rank: $rankStr")
    val num = level.toInt()
    if (num < 0 || num > 9) throw Error("invalid rank: $rankStr")
    return when (letter.lowercase()) {
        "k" -> -num
        "d" -> num - 1
        "p" -> num + 9
        else -> throw Error("impossible")
    }
}

