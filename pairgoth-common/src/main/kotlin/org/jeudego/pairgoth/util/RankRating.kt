package org.jeudego.pairgoth.util

import kotlin.math.floor
import kotlin.math.roundToInt

// Canonical rating ↔ rank conversion, matching the EGD GoR scheme:
//   1d centred at 2100, step 100, lower-edge bucketing (1d = [2050, 2150), 1k = [1950, 2050)).
//   FFG scale = EGD scale - 2050 (FFG natively uses 0 as the 1k/1d boundary).
//   Pro grades: 1p = 2700, step 30 up to 9p = 2940. Pro overlaps amateur 7d..9d
//   in strength; the `pro` field on Player carries the title separately from rank.
// Reference: https://www.europeangodatabase.eu/EGD/EGF_rating_system.php
//            EGD GoR2Rank (statistics_functions.php) gives the same buckets via round($gor/100)-1.

const val MIN_RANK: Int = -30 // 30k
const val MAX_RANK: Int = 8   // 9d
const val MIN_PRO: Int = 1
const val MAX_PRO: Int = 9
const val PRO_BASE_RATING: Int = 2700  // 1p
const val PRO_STEP: Int = 30           // step between pro grades

fun ratingToRank(rating: Int): Int =
    floor((rating - 2050) / 100.0).toInt().coerceIn(MIN_RANK, MAX_RANK)

fun rankToRating(rank: Int): Int = 2050 + 100 * rank

fun ratingToPro(rating: Int): Int =
    ((rating - PRO_BASE_RATING).toDouble() / PRO_STEP).roundToInt().plus(1).coerceIn(MIN_PRO, MAX_PRO)

fun proToRating(pro: Int): Int = PRO_BASE_RATING + PRO_STEP * (pro - 1)

fun displayRank(rank: Int): String = if (rank < 0) "${-rank}k" else "${rank + 1}d"

fun displayRank(rank: Int, pro: Int): String =
    if (pro in MIN_PRO..MAX_PRO) "${pro}p" else displayRank(rank)
