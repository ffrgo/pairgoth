package org.jeudego.pairgoth.util

import kotlin.math.floor

// Canonical rating ↔ rank conversion, matching the EGD GoR scheme:
//   1d centred at 2100, step 100, lower-edge bucketing (1d = [2050, 2150), 1k = [1950, 2050)).
//   FFG scale = EGD scale - 2050 (FFG natively uses 0 as the 1k/1d boundary).
// Reference: https://www.europeangodatabase.eu/EGD/EGF_rating_system.php
//            EGD GoR2Rank (statistics_functions.php) gives the same buckets via round($gor/100)-1.

const val MIN_RANK: Int = -30 // 30k
const val MAX_RANK: Int = 8   // 9d

fun ratingToRank(rating: Int): Int =
    floor((rating - 2050) / 100.0).toInt().coerceIn(MIN_RANK, MAX_RANK)

fun rankToRating(rank: Int): Int = 2050 + 100 * rank

fun displayRank(rank: Int): String = if (rank < 0) "${-rank}k" else "${rank + 1}d"
