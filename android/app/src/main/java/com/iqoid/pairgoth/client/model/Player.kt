package com.iqoid.pairgoth.client.model

import com.google.gson.annotations.SerializedName

data class Player(
    val id: String? = null, // Nullable because it's assigned by the server on registration
    val name: String,
    val firstname: String,
    val country: String,
    val club: String,
    var rank: String,
    var rankAsString: String?,
    val rating: Int,
    val origin: String?,
    val skip: List<Int> = emptyList(), //optional property
    val final: Boolean = false, //optional property
    val egf:String?=null, //optional property
    val ffg:String?=null //optional property
    // ... other relevant properties ...
) {
    companion object {
        private val rankRegex = Regex("(\\d+)([kd])", RegexOption.IGNORE_CASE)

        fun parseRank(rankStr: String): Int {
            val (level, letter) = rankRegex.matchEntire(rankStr)?.destructured ?: throw Error("invalid rank: $rankStr")
            val num = level.toInt()
            if (num < 0 || letter != "k" && letter != "K" && num > 9) throw Error("invalid rank: $rankStr")
            return when (letter.lowercase()) {
                "k" -> -num
                "d" -> num - 1
                else -> throw Error("impossible")
            }
        }
    }
}
